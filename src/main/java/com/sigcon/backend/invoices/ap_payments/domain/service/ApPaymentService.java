package com.sigcon.backend.invoices.ap_payments.domain.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.invoices.ap_payments.application.ApPaymentDTO;
import com.sigcon.backend.invoices.ap_payments.application.CreateApPaymentRequest;
import com.sigcon.backend.invoices.domain.events.ApPaymentProcessedEvent;
import com.sigcon.backend.invoices.ap_payments.domain.model.ApPayment;
import com.sigcon.backend.invoices.ap_payments.domain.repository.ApPaymentRepository;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestion de pagos y abonos a facturas de compra.
 * Registra pagos, actualiza saldos de factura y genera asientos contables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApPaymentService {

    private final ApPaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final JournalEntryService journalEntryService;
    // HU-AP-07 E2 (2026-05-06): chequear que el JE de la factura este POSTED
    // antes de permitir registrar pagos.
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final ApplicationEventPublisher eventPublisher;
    // HU-AP-04 E1: registrar movimiento bancario/caja al pagar para que el
    // pago se vea reflejado en el modulo BNK con su saldo afectado.
    private final FinancialMovementRepository financialMovementRepository;
    private final BankAccountRepository bankAccountRepository;
    private final CashRepository cashRepository;
    private final AuditPublisher auditPublisher;

    private final DataTableSpecificationBuilder<ApPayment> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Registra un pago o abono a una factura de compra.
     * Valida la existencia de la factura, su estado, que el monto no supere
     * el saldo pendiente, unicidad de referencia y periodo contable abierto.
     * Actualiza el saldo pendiente y el estado de la factura.
     *
     * @param request datos del pago a registrar
     * @return ResponseEntity con el pago registrado
     * @throws IllegalArgumentException si la factura no existe, la referencia esta duplicada
     *                                  o el monto supera el saldo
     * @throws IllegalStateException    si la factura esta anulada/liquidada o el periodo cerrado
     */
    @Transactional
    public ResponseEntity<?> registerPayment(CreateApPaymentRequest request) {
        // 1. Buscar factura
        Invoices invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        // 2. Validar estado de la factura
        if (invoice.getStatus() == StatusesInvoices.VOIDED) {
            throw new IllegalStateException("No se puede registrar un pago a una factura anulada");
        }
        if (invoice.getStatus() == StatusesInvoices.SETTLED) {
            throw new IllegalStateException("No se puede registrar un pago a una factura liquidada");
        }
        if (invoice.getStatus() == StatusesInvoices.PAID) {
            throw new IllegalStateException("La factura ya se encuentra totalmente pagada");
        }

        // HU-AP-07 E2 (2026-05-06): no se puede pagar una factura cuyo
        // comprobante contable aun esta en BORRADOR. La obligacion contable
        // debe estar reconocida antes de aceptar pagos. Mensaje exacto del
        // Excel HU para auditoria legal.
        if (invoice.getJournalEntryId() != null) {
            JournalEntry je = journalEntryRepository.findById(invoice.getJournalEntryId()).orElse(null);
            if (je == null || je.getStatus() != JournalEntryStatus.POSTED) {
                String estado = je == null ? "INEXISTENTE" : je.getStatus().name();
                throw new IllegalStateException(
                    "No se puede registrar el pago: la factura aun no esta contabilizada en Contabilidad General "
                    + "(comprobante en estado " + estado + "). Contabilice la factura antes de registrar pagos.");
            }
        } else {
            // Defensa adicional: si la factura existe sin journalEntryId, es un caso
            // anomalo (datos inconsistentes). Bloquear pago hasta que se corrija.
            throw new IllegalStateException(
                "No se puede registrar el pago: la factura no tiene comprobante contable asociado. "
                + "Contacte al administrador para regenerar el asiento.");
        }

        // HU-AP-07: calcular descuento por pronto pago si aplica
        BigDecimal balanceDue = BigDecimal.valueOf(invoice.getBalanceDue());
        BigDecimal earlyDiscount = BigDecimal.ZERO;
        if (invoice.getEarlyPaymentDiscountPct() != null
                && invoice.getEarlyPaymentDiscountDays() != null
                && invoice.getEarlyPaymentDiscountPct() > 0
                && invoice.getInvoiceDate() != null
                && request.getPaymentDate() != null) {
            java.time.LocalDate deadline = invoice.getInvoiceDate()
                    .plusDays(invoice.getEarlyPaymentDiscountDays());
            if (!request.getPaymentDate().isAfter(deadline)) {
                earlyDiscount = balanceDue.multiply(
                        BigDecimal.valueOf(invoice.getEarlyPaymentDiscountPct()))
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                balanceDue = balanceDue.subtract(earlyDiscount);
                log.info("HU-AP-07: descuento pronto pago {}% = ${} aplicado a factura {} "
                                + "(paga antes de {})",
                        invoice.getEarlyPaymentDiscountPct(), earlyDiscount,
                        invoice.getId(), deadline);
            }
        }

        // 3. Validar monto no supere saldo pendiente (despues de descuento)
        if (request.getAmount().compareTo(balanceDue) > 0) {
            throw new IllegalArgumentException(
                    "El monto del pago supera el saldo pendiente. Saldo actual: $" + balanceDue);
        }

        // 4. Validar referencia de pago no duplicada
        if (request.getPaymentReference() != null && !request.getPaymentReference().isBlank()) {
            if (paymentRepository.existsByPaymentReferenceAndDeletedAtIsNull(request.getPaymentReference())) {
                throw new IllegalArgumentException("Ya existe un pago con esta referencia: " + request.getPaymentReference());
            }
        }

        // 4b. AP-08 (idempotencia): bloquear doble registro accidental con misma
        // factura + monto + fecha. Protege contra doble-click en UI cuando el
        // usuario no informa paymentReference.
        if (paymentRepository.existsByInvoice_IdAndAmountAndPaymentDateAndDeletedAtIsNull(
                invoice.getId(), request.getAmount(), request.getPaymentDate())) {
            throw new IllegalArgumentException(
                    "Ya existe un pago identico para esta factura (mismo monto y fecha). "
                    + "Si realmente es un pago separado, informe un paymentReference distinto.");
        }

        // 5. Validar periodo contable abierto. HU-AP-07 E5 (Bloque AR): mensaje literal HU.
        try {
            accountingPeriodService.validatePeriodOpen(request.getPaymentDate());
        } catch (RuntimeException ex) {
            java.time.LocalDate d = request.getPaymentDate();
            String yyyymm = d != null ? String.format("%04d-%02d", d.getYear(), d.getMonthValue()) : "indefinido";
            throw new IllegalStateException(
                "El período " + yyyymm + " está cerrado. Use una fecha en el período vigente.");
        }

        // HU-AP-04 E3 / HU-AP-07 E3 (Bloque AR): validar fondos suficientes en la
        // cuenta bancaria o caja antes de crear el pago. Suma initialBalance +
        // sum(financial_movements) + creditLimit (si overdraft permitido).
        validateSufficientFunds(request.getBankAccountId(), request.getCashId(), request.getAmount());

        // 6. Crear el pago
        ApPayment payment = ApPayment.builder()
                .invoice(invoice)
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate())
                .paymentReference(request.getPaymentReference())
                .paymentMethod(request.getPaymentMethod())
                .bankAccountId(request.getBankAccountId())
                .cashId(request.getCashId())
                .checkId(request.getCheckId())
                .status("COMPLETED")
                .notes(request.getNotes())
                .build();

        payment = paymentRepository.save(payment);

        // 7. Actualizar saldo de la factura
        double newBalance = invoice.getBalanceDue() - request.getAmount().doubleValue();
        invoice.setBalanceDue(newBalance);

        if (newBalance <= 0) {
            invoice.setStatus(StatusesInvoices.PAID);
        } else {
            invoice.setStatus(StatusesInvoices.PARTIALLY_PAID);
        }
        invoiceRepository.save(invoice);

        // 8. Generar asiento contable (Debito CxP / Credito Banco/Caja)
        try {
            // AP-02: Debito CxP proveedores (PUC 2205) / Credito Bancos (PUC 1110)
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AP_PROVEEDORES);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.BANCOS_DEFAULT);

            String thirdPartyNit = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getNit() : null;

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(request.getPaymentDate())
                    .description("Pago factura " + invoice.getResolutionInvoice()
                            + " - Ref: " + (request.getPaymentReference() != null ? request.getPaymentReference() : "N/A"))
                    .sourceModule(JournalSourceModule.AP)
                    .sourceId(payment.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(request.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Pago CxP factura " + invoice.getResolutionInvoice())
                                    .thirdPartyNit(thirdPartyNit)
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(request.getAmount())
                                    .description("Egreso " + request.getPaymentMethod()
                                            + " factura " + invoice.getResolutionInvoice())
                                    .thirdPartyNit(thirdPartyNit)
                                    .build()
                    ))
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            payment.setJournalEntryId(je.getId());
            paymentRepository.save(payment);
            log.info("Asiento contable {} generado para pago {} de factura {}",
                    je.getId(), payment.getId(), invoice.getId());

            // HU-AP-04 E1: registrar movimiento financiero en BNK (egreso). Antes
            // del fix el pago solo afectaba CxC contable pero NO se reflejaba en
            // el saldo de la cuenta bancaria/caja. El monto va negativo porque
            // es un egreso. Solo si el pago indica una cuenta bancaria o caja.
            try {
                FinancialMovement fm = null;
                if (request.getBankAccountId() != null) {
                    BankAccount ba = bankAccountRepository.findById(request.getBankAccountId())
                            .orElse(null);
                    if (ba != null) {
                        fm = FinancialMovement.builder()
                                .bankAccount(ba)
                                .movementDate(request.getPaymentDate())
                                .amount(request.getAmount().negate())
                                .description("Pago factura " + invoice.getResolutionInvoice()
                                        + " - Ref: " + (request.getPaymentReference() != null
                                                ? request.getPaymentReference() : "N/A"))
                                .externalReference(request.getPaymentReference())
                                .sourceType(FinancialMovementSourceType.MANUAL)
                                .flowActivity("OPERATIVA")
                                // QA-BLOQUE-AY (2026-05-06): vincular el FM al JE
                                // del pago para que la columna "Conciliacion" de
                                // /financial-movements muestre "Conciliado" y el
                                // emparejado en /bank-reconciliation muestre el
                                // numero del comprobante. El JE ya esta en CG
                                // como sourceModule=AP, asi que el FM hereda esa
                                // referencia automaticamente.
                                .matchedJournalEntryId(je.getId())
                                .build();
                    }
                } else if (request.getCashId() != null) {
                    Cash cash = cashRepository.findById(request.getCashId()).orElse(null);
                    if (cash != null) {
                        fm = FinancialMovement.builder()
                                .cash(cash)
                                .movementDate(request.getPaymentDate())
                                .amount(request.getAmount().negate())
                                .description("Pago factura " + invoice.getResolutionInvoice()
                                        + " - Ref: " + (request.getPaymentReference() != null
                                                ? request.getPaymentReference() : "N/A"))
                                .externalReference(request.getPaymentReference())
                                .sourceType(FinancialMovementSourceType.MANUAL)
                                .flowActivity("OPERATIVA")
                                .matchedJournalEntryId(je.getId())
                                .build();
                    }
                }
                if (fm != null) {
                    fm = financialMovementRepository.save(fm);
                    // QA-BLOQUE-AY HU-AP-08 / HU-AP-03 (2026-05-06): auto-conciliar el
                    // pago con el FM recien creado. El sistema sabe que ese
                    // movimiento corresponde 1:1 al pago, asi que no tiene sentido
                    // que el contador lo concilie manualmente. Sin esto, el pago
                    // queda bankMovementId=null y el endpoint /settle rechaza con
                    // "pagos no conciliados en BNK" aunque el flujo se completo
                    // automaticamente.
                    payment.setBankMovementId(fm.getId());
                    payment.setReconciledAt(java.time.LocalDateTime.now());
                    paymentRepository.save(payment);
                    log.info("Movimiento financiero {} (matchedJE={}) generado para pago {} (auto-conciliado)",
                            fm.getId(), je.getId(), payment.getId());
                }
            } catch (RuntimeException e) {
                // Si falla el FM, NO rompemos el pago (ya esta guardado el JE).
                // El admin puede crear el FM manualmente despues. Logueamos.
                log.warn("No se pudo registrar movimiento financiero para pago {}: {}",
                        payment.getId(), e.getMessage());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para pago {}: {}", payment.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar el pago: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para pago {}", payment.getId(), e);
            throw e;
        }

        // Publicar evento de pago procesado
        try {
            eventPublisher.publishEvent(new ApPaymentProcessedEvent(
                    this, payment.getId(), invoice.getId(),
                    request.getAmount(), request.getBankAccountId()));
        } catch (Exception e) {
            log.warn("No se pudo publicar evento ApPaymentProcessedEvent para pago {}: {}",
                    payment.getId(), e.getMessage());
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Pago registrado exitosamente"), Optional.of(toDTO(payment))));
    }

    /**
     * QA Integracion AAEF (2026-05-27): revierte de forma DETERMINISTA un pago (AP)
     * registrado via AAEF, identificado por su referencia (= DocumentId del PAY
     * original). Es el efecto que debe producir {@code Type=PAY, Status=REVERSED}:
     * <ol>
     *   <li>deshace el asiento contable del pago (deleteEntry si BORRADOR,
     *       reverseEntry/storno si CONTABILIZADO);</li>
     *   <li>anula el movimiento financiero (egreso) que el pago genero en BNK,
     *       restaurando el saldo de la cuenta/caja;</li>
     *   <li>restaura el saldo de la factura y recalcula su estado (PENDING si no
     *       quedan abonos, PARTIALLY_PAID si conserva otros);</li>
     *   <li>marca el pago como REVERSED preservando la fila para auditoria.</li>
     * </ol>
     *
     * <p>Si no hay un pago ACTIVO con esa referencia sobre la factura, lanza
     * {@link IllegalArgumentException} con mensaje claro (el procesador AAEF lo
     * traduce a errorCode {@code MISSING_PAYMENT}).
     *
     * @return id del asiento de reversion (REV) si el JE del pago estaba
     *         CONTABILIZADO, o {@code null} si estaba en BORRADOR y se elimino.
     */
    @Transactional
    public Long reversePaymentByReference(Long invoiceId, String reference,
                                          BigDecimal expectedAmount, String reason) {
        Invoices invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La factura referenciada no fue encontrada para revertir el pago."));

        ApPayment target = paymentRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId).stream()
                .filter(p -> reference != null && reference.equals(p.getPaymentReference()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No se encontro un pago activo con referencia '" + reference + "' sobre la factura "
                        + invoice.getResolutionInvoice() + " para revertir. Para revertir un pago via AAEF, "
                        + "reenvie la transaccion con Status=REVERSED usando el mismo DocumentId del pago original."));

        // 1. Deshacer el asiento contable del pago: delete si DRAFT, reverse si POSTED.
        Long reversalJeId = null;
        if (target.getJournalEntryId() != null) {
            try {
                journalEntryService.deleteEntry(target.getJournalEntryId());
            } catch (IllegalStateException posted) {
                JournalEntryDTO rev = journalEntryService.reverseEntry(
                        target.getJournalEntryId(),
                        "Reversion pago AAEF (ref " + reference + "): " + reason, "sistema-aaef");
                reversalJeId = rev != null ? rev.getId() : null;
            }
        }

        // 2. Compensar el movimiento financiero (egreso BNK) que genero el pago
        //    creando un movimiento inverso (ingreso) que restaura el saldo de la
        //    cuenta/caja. FinancialMovement no es soft-delete; el inverso preserva
        //    la trazabilidad (equivale en BNK al storno contable del JE).
        if (target.getBankMovementId() != null) {
            financialMovementRepository.findById(target.getBankMovementId()).ifPresent(orig -> {
                FinancialMovement comp = FinancialMovement.builder()
                        .bankAccount(orig.getBankAccount())
                        .cash(orig.getCash())
                        .movementDate(java.time.LocalDate.now())
                        .amount(orig.getAmount().negate())
                        .description("Reversion pago AAEF (ref " + reference
                                + ") - compensa movimiento #" + orig.getId())
                        .externalReference(reference)
                        .sourceType(FinancialMovementSourceType.MANUAL)
                        .flowActivity(orig.getFlowActivity())
                        .build();
                financialMovementRepository.save(comp);
            });
        }

        // 3. Restaurar saldo de la factura.
        double restored = invoice.getBalanceDue() + target.getAmount().doubleValue();
        invoice.setBalanceDue(restored);

        // 4. Marcar el pago como revertido (preserva la fila para auditoria).
        target.setStatus("REVERSED");
        target.setDeletedAt(java.time.LocalDateTime.now());
        paymentRepository.save(target);

        // 5. Recalcular estado: si quedan abonos activos -> PARTIALLY_PAID, si no -> PENDING.
        boolean hayAbonos = !paymentRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId).isEmpty();
        invoice.setStatus(hayAbonos ? StatusesInvoices.PARTIALLY_PAID : StatusesInvoices.PENDING);
        invoiceRepository.save(invoice);

        auditPublisher.publishUpdate(AuditModule.AP, "ApPayment", target.getId(),
                "Pago revertido via AAEF (ref " + reference + "). Factura "
                + invoice.getResolutionInvoice() + ": saldo restaurado a $" + restored
                + ", estado -> " + invoice.getStatus());

        log.info("AAEF reverse pago ref={} factura={} -> estado {} saldo {} (revJE={})",
                reference, invoice.getResolutionInvoice(), invoice.getStatus(), restored, reversalJeId);
        return reversalJeId;
    }

    /**
     * Consulta pagos con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de pagos
     */
    public ResponseEntity<?> getPayments(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ApPayment> spec = specBuilder.build(request);
        Page<ApPaymentDTO> data = paymentRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene todos los pagos asociados a una factura especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de pagos de la factura
     */
    public ResponseEntity<?> getPaymentsByInvoice(Long invoiceId) {
        List<ApPaymentDTO> payments = paymentRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Pagos de la factura"), Optional.of(payments)));
    }

    /**
     * Convierte una entidad ApPayment a su DTO de lectura.
     *
     * @param payment entidad a convertir
     * @return DTO con los datos del pago
     */
    private ApPaymentDTO toDTO(ApPayment payment) {
        return ApPaymentDTO.builder()
                .id(payment.getId())
                .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
                .invoiceNumber(payment.getInvoice() != null ? payment.getInvoice().getResolutionInvoice() : null)
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentReference(payment.getPaymentReference())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .notes(payment.getNotes())
                .build();
    }

    /**
     * HU-AP-04 E3 / HU-AP-07 E3 (Bloque AR): valida que la cuenta bancaria o
     * caja tenga fondos suficientes para el pago/abono. El saldo disponible
     * es initial_balance + sum(financial_movements) (incluye sus signos), mas
     * el credit_limit si la cuenta admite sobregiro.
     *
     * Si bankAccountId Y cashId son null, no valida (puede ser pago en cheque
     * que ya consumio fondos al emitirse o forma de pago sin medio de
     * salida directo).
     */
    private void validateSufficientFunds(Long bankAccountId, Long cashId, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        if (bankAccountId != null) {
            BankAccount ba = bankAccountRepository.findById(bankAccountId).orElse(null);
            if (ba == null) {
                return;
            }
            BigDecimal initial = ba.getInitialBalance() != null ? ba.getInitialBalance() : BigDecimal.ZERO;
            BigDecimal moved = financialMovementRepository.sumAmountByBankAccountId(bankAccountId);
            if (moved == null) {
                moved = BigDecimal.ZERO;
            }
            BigDecimal credit = (Boolean.TRUE.equals(ba.getAllowsOverdraft()) && ba.getCreditLimit() != null)
                    ? ba.getCreditLimit() : BigDecimal.ZERO;
            BigDecimal available = initial.add(moved).add(credit);
            if (amount.compareTo(available) > 0) {
                throw new IllegalArgumentException(
                        "El saldo disponible en la cuenta seleccionada no es suficiente "
                        + "para registrar este abono. Saldo disponible: $" + available);
            }
        } else if (cashId != null) {
            Cash cash = cashRepository.findById(cashId).orElse(null);
            if (cash == null) {
                return;
            }
            BigDecimal initial = cash.getInitialBalance() != null ? cash.getInitialBalance() : BigDecimal.ZERO;
            BigDecimal moved = financialMovementRepository.sumAmountByCashId(cashId);
            if (moved == null) {
                moved = BigDecimal.ZERO;
            }
            BigDecimal available = initial.add(moved);
            if (amount.compareTo(available) > 0) {
                throw new IllegalArgumentException(
                        "El saldo disponible en la caja seleccionada no es suficiente "
                        + "para registrar este abono. Saldo disponible: $" + available);
            }
        }
    }
}
