package com.sigcon.backend.invoices.ap_payments.domain.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.invoices.ap_payments.application.ApAdvanceDTO;
import com.sigcon.backend.invoices.ap_payments.application.ApplyAdvanceRequest;
import com.sigcon.backend.invoices.ap_payments.application.CreateApAdvanceRequest;
import com.sigcon.backend.invoices.ap_payments.domain.model.ApAdvance;
import com.sigcon.backend.invoices.ap_payments.domain.model.ApAdvanceApplication;
import com.sigcon.backend.invoices.ap_payments.domain.repository.ApAdvanceRepository;
import com.sigcon.backend.invoices.ap_payments.domain.repository.ApAdvanceApplicationRepository;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
// HU-AP-05 E3 + AP-15 (2026-04-28): registrar movimiento financiero en BNK
// para que el anticipo descuente fondos de la cuenta bancaria/caja real.
import com.sigcon.backend.banks.bankaccounts.domain.model.BankAccount;
import com.sigcon.backend.banks.bankaccounts.domain.repository.BankAccountRepository;
import com.sigcon.backend.banks.cash_management.domain.model.Cash;
import com.sigcon.backend.banks.cash_management.domain.repository.CashRepository;
import com.sigcon.backend.banks.financialmovements.domain.model.FinancialMovement;
import com.sigcon.backend.banks.financialmovements.domain.model.enums.FinancialMovementSourceType;
import com.sigcon.backend.banks.financialmovements.domain.repository.FinancialMovementRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para gestion de anticipos a proveedores.
 * Permite registrar anticipos y aplicarlos a facturas de compra,
 * con generacion automatica de asientos contables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApAdvanceService {

    private final ApAdvanceRepository advanceRepository;
    private final ApAdvanceApplicationRepository applicationRepository;
    private final InvoiceRepository invoiceRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final JournalEntryService journalEntryService;
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;
    // HU-AP-05 E3 + AP-15: dependencias para registrar movimiento bancario.
    private final BankAccountRepository bankAccountRepository;
    private final CashRepository cashRepository;
    private final FinancialMovementRepository financialMovementRepository;

    private final DataTableSpecificationBuilder<ApAdvance> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Registra un nuevo anticipo a un proveedor.
     * Valida la existencia del tercero, que este activo y que el periodo este abierto.
     *
     * @param request datos del anticipo a registrar
     * @return ResponseEntity con el anticipo creado
     * @throws IllegalArgumentException si el tercero no existe
     * @throws IllegalStateException    si el tercero esta inactivo o el periodo cerrado
     */
    @Transactional
    public ResponseEntity<?> registerAdvance(CreateApAdvanceRequest request) {
        // 1. Validar tercero existe y esta activo
        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new IllegalArgumentException("El tercero no fue encontrado"));

        // HU-AP-05+07 (2026-04-28): el catalogo `third_party_status_catalog`
        // guarda el name en español (ACTIVO/INACTIVO/BLOQUEADO). Antes el
        // codigo comparaba con "ACTIVE" y bloqueaba TODO anticipo aunque el
        // proveedor estuviera activo. Resultado: error "El tercero no se
        // encuentra activo" en flujo de anticipos, descuentos pronto pago, etc.
        if (thirdParty.getStatus() != null && !"ACTIVO".equalsIgnoreCase(thirdParty.getStatus().getName())) {
            throw new IllegalStateException(
                    "El proveedor no esta activo o no existe. Verifique los datos en el modulo de Terceros.");
        }

        // 2. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getAdvanceDate());

        // HU-AP-05 E3 (Bloque AR): validar fondos suficientes en cuenta/caja.
        validateSufficientFunds(request.getBankAccountId(), request.getCashId(), request.getAmount());

        // 3. Crear anticipo
        ApAdvance advance = ApAdvance.builder()
                .thirdParty(thirdParty)
                .amount(request.getAmount())
                .advanceDate(request.getAdvanceDate())
                .status("PENDING")
                .bankAccountId(request.getBankAccountId())
                .cashId(request.getCashId())
                .notes(request.getNotes())
                .build();

        advance = advanceRepository.save(advance);
        auditPublisher.publishCreate(AuditModule.AP, "ApAdvance", advance.getId(), "ApAdvance creado id=" + advance.getId());

        // 4. Generar asiento contable (Debito Anticipo / Credito Banco/Caja)
        try {
            // AP-09: Debito Anticipos a proveedores (PUC 1330) / Credito Bancos (PUC 1110)
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AP_ANTICIPOS);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.BANCOS_DEFAULT);

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(request.getAdvanceDate())
                    .description("Anticipo a proveedor " + thirdParty.getBusinessName())
                    .sourceModule(JournalSourceModule.AP)
                    .sourceId(advance.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(request.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Anticipo a " + thirdParty.getBusinessName())
                                    .thirdPartyNit(thirdParty.getNit())
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(request.getAmount())
                                    .description("Egreso anticipo " + thirdParty.getBusinessName())
                                    .thirdPartyNit(thirdParty.getNit())
                                    .build()
                    ))
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            advance.setJournalEntryId(je.getId());
            advanceRepository.save(advance);
            auditPublisher.publishCreate(AuditModule.AP, "ApAdvance", advance.getId(), "ApAdvance creado id=" + advance.getId());
            log.info("Asiento contable {} generado para anticipo {}", je.getId(), advance.getId());

            // HU-AP-05 E3 + AP-15 (2026-04-28): registrar movimiento financiero
            // en BNK (egreso). Antes el anticipo solo afectaba el contable; el
            // saldo de la cuenta bancaria nunca bajaba ni aparecia el movimiento
            // en /cash-and-banks/financial-movements.
            try {
                FinancialMovement fm = null;
                if (request.getBankAccountId() != null) {
                    BankAccount ba = bankAccountRepository.findById(request.getBankAccountId()).orElse(null);
                    if (ba != null) {
                        fm = FinancialMovement.builder()
                                .bankAccount(ba)
                                .movementDate(request.getAdvanceDate())
                                .amount(request.getAmount().negate())
                                .description("Anticipo a proveedor " + thirdParty.getBusinessName())
                                .sourceType(FinancialMovementSourceType.MANUAL)
                                .flowActivity("OPERATIVA")
                                // QA-BLOQUE-AY (2026-05-06): vincular FM al JE
                                // del anticipo para que aparezca conciliado.
                                .matchedJournalEntryId(je.getId())
                                .build();
                    }
                } else if (request.getCashId() != null) {
                    Cash cash = cashRepository.findById(request.getCashId()).orElse(null);
                    if (cash != null) {
                        fm = FinancialMovement.builder()
                                .cash(cash)
                                .movementDate(request.getAdvanceDate())
                                .amount(request.getAmount().negate())
                                .description("Anticipo a proveedor " + thirdParty.getBusinessName())
                                .sourceType(FinancialMovementSourceType.MANUAL)
                                .flowActivity("OPERATIVA")
                                .matchedJournalEntryId(je.getId())
                                .build();
                    }
                }
                if (fm != null) {
                    financialMovementRepository.save(fm);
                    log.info("Movimiento financiero generado para anticipo {} (matchedJE={})",
                            advance.getId(), je.getId());
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo registrar movimiento financiero para anticipo {}: {}",
                        advance.getId(), e.getMessage());
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para anticipo {}: {}", advance.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar el anticipo: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para anticipo {}", advance.getId(), e);
            throw e;
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Anticipo registrado exitosamente"), Optional.of(toDTO(advance))));
    }

    /**
     * Aplica un anticipo existente a una factura de compra.
     * Valida que el anticipo este pendiente, que la factura pertenezca al mismo tercero,
     * y que el monto no supere ni el anticipo ni el saldo de la factura.
     *
     * @param advanceId identificador del anticipo
     * @param request   datos de la aplicacion (factura y monto)
     * @return ResponseEntity con el anticipo actualizado
     * @throws IllegalArgumentException si el anticipo o la factura no existen, o los montos son invalidos
     * @throws IllegalStateException    si el anticipo no esta pendiente o el tercero no coincide
     */
    @Transactional
    public ResponseEntity<?> applyAdvance(Long advanceId, ApplyAdvanceRequest request) {
        // 1. Buscar anticipo y validar estado.
        // AP-RF-05 E6 (Bloque DV): el anticipo puede aplicarse a VARIAS facturas
        // mientras tenga disponible. Se permite PENDING o PARTIALLY_APPLIED; se
        // bloquea APPLIED (sin disponible) y CANCELLED (anulado).
        ApAdvance advance = advanceRepository.findById(advanceId)
                .orElseThrow(() -> new IllegalArgumentException("El anticipo no fue encontrado"));

        if ("CANCELLED".equals(advance.getStatus())) {
            throw new IllegalStateException("El anticipo esta anulado y no puede aplicarse.");
        }
        BigDecimal available = availableOf(advance);
        if (available.signum() <= 0) {
            throw new IllegalStateException(
                    "El anticipo ya fue aplicado en su totalidad; no tiene saldo disponible.");
        }

        // 2. Validar monto (> 0 y <= disponible).
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        if (request.getAmount().compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    "Excede el monto del anticipo. Disponible: $" + available.stripTrailingZeros().toPlainString());
        }

        // 3. Buscar factura y validar tercero + saldo.
        Invoices invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        if (invoice.getThirdParty() == null || advance.getThirdParty() == null
                || !invoice.getThirdParty().getId().equals(advance.getThirdParty().getId())) {
            throw new IllegalStateException(
                    "El anticipo no corresponde al mismo tercero de la factura");
        }
        // AP-RF-05: solo facturas PENDIENTE o PARCIALMENTE PAGADA con saldo > 0.
        if (invoice.getStatus() != StatusesInvoices.PENDING
                && invoice.getStatus() != StatusesInvoices.PARTIALLY_PAID) {
            throw new IllegalStateException(
                    "Solo se puede aplicar el anticipo a facturas pendientes o parcialmente pagadas.");
        }
        BigDecimal balanceDue = BigDecimal.valueOf(invoice.getBalanceDue());
        if (balanceDue.signum() <= 0) {
            throw new IllegalStateException("La factura seleccionada no tiene saldo pendiente.");
        }
        if (request.getAmount().compareTo(balanceDue) > 0) {
            throw new IllegalArgumentException(
                    "El monto a aplicar supera el saldo pendiente de la factura. Saldo: $" + balanceDue);
        }

        BigDecimal applyAmount = request.getAmount();

        // 4. Generar asiento de la aplicacion (Debito CxP 2205 / Credito Anticipos 1330).
        Long appJeId = null;
        try {
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AP_PROVEEDORES);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AP_ANTICIPOS);
            String tpNit = advance.getThirdParty().getNit();
            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(java.time.LocalDate.now())
                    .description("Aplicacion anticipo " + advanceCode(advance)
                            + " a factura " + (invoice.getResolutionInvoice() != null
                                    ? invoice.getResolutionInvoice() : invoice.getId()))
                    .sourceModule(JournalSourceModule.AP)
                    .sourceId(advance.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(applyAmount)
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Aplicacion anticipo a CxP")
                                    .thirdPartyNit(tpNit)
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(applyAmount)
                                    .description("Cruce anticipo")
                                    .thirdPartyNit(tpNit)
                                    .build()))
                    .build();
            appJeId = journalEntryService.createEntry(jeRequest, "sistema").getId();
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento de aplicacion de anticipo {}: {}", advanceId, e.getMessage());
            throw new IllegalStateException("No se pudo aplicar el anticipo: " + e.getMessage(), e);
        }

        // 5. Registrar la aplicacion (fila hija) para soportar reversion por factura.
        ApAdvanceApplication app = ApAdvanceApplication.builder()
                .advanceId(advance.getId())
                .invoiceId(invoice.getId())
                .amount(applyAmount)
                .journalEntryId(appJeId)
                .status("ACTIVE")
                .build();
        applicationRepository.save(app);

        // 6. Actualizar anticipo: acumular aplicado, calcular disponible y estado.
        BigDecimal newApplied = nz(advance.getAppliedAmount()).add(applyAmount);
        advance.setAppliedAmount(newApplied);
        advance.setAppliedInvoiceId(invoice.getId());
        advance.setAppliedAt(LocalDateTime.now());
        advance.setStatus(newApplied.compareTo(advance.getAmount()) >= 0 ? "APPLIED" : "PARTIALLY_APPLIED");
        advanceRepository.save(advance);

        // 7. Actualizar saldo de la factura.
        double newBalance = invoice.getBalanceDue() - applyAmount.doubleValue();
        invoice.setBalanceDue(newBalance);
        invoice.setStatus(newBalance <= 0 ? StatusesInvoices.PAID : StatusesInvoices.PARTIALLY_PAID);
        invoiceRepository.save(invoice);

        auditPublisher.publishUpdate(AuditModule.AP, "ApAdvance", advance.getId(),
                "Aplicacion de anticipo " + advanceCode(advance) + " a factura " + invoice.getId()
                        + " por $" + applyAmount + " (disponible restante $" + availableOf(advance) + ")");

        log.info("Anticipo {} aplicado a factura {} por ${} (estado {})",
                advanceId, invoice.getId(), applyAmount, advance.getStatus());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Anticipo aplicado exitosamente a la factura"), Optional.of(toDTO(advance))));
    }

    /** Disponible del anticipo = monto total - aplicado acumulado. */
    private BigDecimal availableOf(ApAdvance advance) {
        return advance.getAmount().subtract(nz(advance.getAppliedAmount()));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Codigo legible del anticipo (ANT-AAAA-NNNNNN) derivado del id+anio. */
    private String advanceCode(ApAdvance a) {
        int year = a.getAdvanceDate() != null ? a.getAdvanceDate().getYear() : 0;
        return String.format("ANT-%04d-%06d", year, a.getId());
    }

    /**
     * AP-RF-05 E7 (Bloque DV): revierte UNA aplicacion del anticipo sobre su
     * factura destino. Restaura el saldo de la factura, devuelve la disponibilidad
     * al anticipo, reversa el asiento de la aplicacion y marca la fila como REVERSED.
     * Es el paso previo obligatorio para anular un anticipo APLICADO/PARCIAL.
     */
    @Transactional
    public ResponseEntity<?> reverseApplication(Long advanceId, Long applicationId, String reason) {
        ApAdvance advance = advanceRepository.findById(advanceId)
                .orElseThrow(() -> new IllegalArgumentException("El anticipo no fue encontrado"));
        ApAdvanceApplication app = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("La aplicacion no fue encontrada"));
        if (!app.getAdvanceId().equals(advanceId)) {
            throw new IllegalArgumentException("La aplicacion no pertenece a este anticipo.");
        }
        if (!"ACTIVE".equals(app.getStatus())) {
            throw new IllegalStateException("La aplicacion ya fue revertida.");
        }

        // 1. Restaurar saldo de la factura destino + recalcular estado.
        Invoices invoice = invoiceRepository.findById(app.getInvoiceId()).orElse(null);
        if (invoice != null) {
            double restored = invoice.getBalanceDue() + app.getAmount().doubleValue();
            invoice.setBalanceDue(restored);
            double total = invoice.getTotalPayment() != null ? invoice.getTotalPayment() : restored;
            if (restored <= 0) {
                invoice.setStatus(StatusesInvoices.PAID);
            } else if (restored >= total) {
                invoice.setStatus(StatusesInvoices.PENDING);
            } else {
                invoice.setStatus(StatusesInvoices.PARTIALLY_PAID);
            }
            invoiceRepository.save(invoice);
        }

        // 2. Reversar el asiento de la aplicacion (DRAFT -> delete, POSTED -> reverse).
        reverseJournalEntry(app.getJournalEntryId(),
                "Reversion aplicacion anticipo " + advanceCode(advance));

        // 3. Marcar aplicacion como revertida.
        app.setStatus("REVERSED");
        app.setReversedAt(LocalDateTime.now());
        app.setReverseReason(reason);
        applicationRepository.save(app);

        // 4. Devolver disponibilidad y recalcular estado del anticipo.
        BigDecimal newApplied = nz(advance.getAppliedAmount()).subtract(app.getAmount());
        if (newApplied.signum() < 0) newApplied = BigDecimal.ZERO;
        advance.setAppliedAmount(newApplied);
        if (newApplied.signum() == 0) {
            advance.setStatus("PENDING");
            advance.setAppliedInvoiceId(null);
        } else {
            advance.setStatus("PARTIALLY_APPLIED");
        }
        advanceRepository.save(advance);

        auditPublisher.publishUpdate(AuditModule.AP, "ApAdvance", advance.getId(),
                "Reversion de aplicacion " + applicationId + " del anticipo " + advanceCode(advance)
                        + " (factura " + app.getInvoiceId() + ", $" + app.getAmount() + ")"
                        + (reason != null && !reason.isBlank() ? " | motivo: " + reason : ""));

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Aplicacion revertida; saldo de la factura restaurado"),
                        Optional.of(toDTO(advance))));
    }

    /**
     * AP-RF-05 E7 (Bloque DV): anula un anticipo. Solo permitido si NO tiene
     * aplicaciones vigentes (estado PENDING). Reversa el asiento de registro,
     * libera los fondos con un movimiento financiero compensatorio (ingreso) y
     * deja el anticipo en estado CANCELLED. Un anticipo APLICADO/PARCIAL debe
     * revertirse primero sobre la(s) factura(s) destino.
     */
    @Transactional
    public ResponseEntity<?> voidAdvance(Long advanceId, String reason) {
        ApAdvance advance = advanceRepository.findById(advanceId)
                .orElseThrow(() -> new IllegalArgumentException("El anticipo no fue encontrado"));

        if ("CANCELLED".equals(advance.getStatus())) {
            return ResponseEntity.ok(
                    SuccessRespondJson.getSuccessRespondMessage(
                            Optional.of("El anticipo ya se encuentra anulado"), Optional.of(toDTO(advance))));
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Debe ingresar el motivo de anulacion (minimo 10 caracteres).");
        }
        if (nz(advance.getAppliedAmount()).signum() > 0 || !"PENDING".equals(advance.getStatus())) {
            throw new IllegalStateException(
                    "El anticipo tiene aplicaciones sobre facturas. Primero revierta la aplicacion sobre "
                    + "la(s) factura(s) destino (restaurando su saldo) antes de anular el anticipo.");
        }

        // 1. Reversar el asiento de registro (Debito 1330 / Credito Bancos).
        Long reversalJeId = reverseJournalEntry(advance.getJournalEntryId(),
                "Anulacion anticipo " + advanceCode(advance) + ": " + reason);

        // 2. Liberar fondos: movimiento financiero compensatorio (ingreso) en BNK.
        try {
            FinancialMovement fm = null;
            String desc = "Reversion anticipo " + advanceCode(advance) + " (anulacion)";
            if (advance.getBankAccountId() != null) {
                BankAccount ba = bankAccountRepository.findById(advance.getBankAccountId()).orElse(null);
                if (ba != null) {
                    fm = FinancialMovement.builder().bankAccount(ba)
                            .movementDate(java.time.LocalDate.now()).amount(advance.getAmount())
                            .description(desc).sourceType(FinancialMovementSourceType.MANUAL)
                            .flowActivity("OPERATIVA").matchedJournalEntryId(reversalJeId).build();
                }
            } else if (advance.getCashId() != null) {
                Cash cash = cashRepository.findById(advance.getCashId()).orElse(null);
                if (cash != null) {
                    fm = FinancialMovement.builder().cash(cash)
                            .movementDate(java.time.LocalDate.now()).amount(advance.getAmount())
                            .description(desc).sourceType(FinancialMovementSourceType.MANUAL)
                            .flowActivity("OPERATIVA").matchedJournalEntryId(reversalJeId).build();
                }
            }
            if (fm != null) financialMovementRepository.save(fm);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar movimiento compensatorio al anular anticipo {}: {}",
                    advanceId, e.getMessage());
        }

        // 3. Marcar anulado.
        advance.setStatus("CANCELLED");
        advance.setNotes((advance.getNotes() != null ? advance.getNotes() + " | " : "")
                + "ANULADO: " + reason);
        advanceRepository.save(advance);

        auditPublisher.publishUpdate(AuditModule.AP, "ApAdvance", advance.getId(),
                "Anulacion del anticipo " + advanceCode(advance) + " | motivo: " + reason);

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Anticipo anulado correctamente; fondos liberados"),
                        Optional.of(toDTO(advance))));
    }

    /** Lista las aplicaciones de un anticipo (para el detalle/vista). */
    public ResponseEntity<?> getApplications(Long advanceId) {
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (ApAdvanceApplication a : applicationRepository
                .findByAdvanceIdAndDeletedAtIsNullOrderByCreatedAtAsc(advanceId)) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", a.getId());
            m.put("invoiceId", a.getInvoiceId());
            Invoices inv = invoiceRepository.findById(a.getInvoiceId()).orElse(null);
            m.put("invoiceNumber", inv != null ? inv.getResolutionInvoice() : null);
            m.put("amount", a.getAmount());
            m.put("status", a.getStatus());
            m.put("appliedAt", a.getCreatedAt());
            rows.add(m);
        }
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Aplicaciones del anticipo"), Optional.of(rows)));
    }

    /**
     * Reversa un asiento contable por id: si esta en BORRADOR lo elimina; si esta
     * CONTABILIZADO genera el asiento inverso (storno). Devuelve el id del asiento
     * inverso si aplica (o null). Tolerante: errores no rompen el flujo padre.
     */
    private Long reverseJournalEntry(Long journalEntryId, String description) {
        if (journalEntryId == null) return null;
        JournalEntry je = journalEntryRepository.findById(journalEntryId).orElse(null);
        if (je == null) return null;
        try {
            if (je.getStatus() == JournalEntryStatus.DRAFT) {
                journalEntryService.deleteEntry(journalEntryId);
                return null;
            } else if (je.getStatus() == JournalEntryStatus.POSTED) {
                return journalEntryService.reverseEntry(journalEntryId, description, "sistema").getId();
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo reversar el asiento {}: {}", journalEntryId, e.getMessage());
        }
        return null;
    }

    /**
     * Consulta anticipos con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de anticipos
     */
    public ResponseEntity<?> getAdvances(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        // RF-28 (Notas Tecnicas CXP): ordenar por defecto por fecha ascendente
        // (anticipos mas antiguos primero) para facilitar la deteccion visual de
        // saldos antiguos sin aplicar. La tabla del frontend tiene el ordenamiento
        // deshabilitado, asi que el orden lo define el backend.
        org.springframework.data.domain.Sort sort =
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.asc("advanceDate"));
        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength, sort);

        Specification<ApAdvance> spec = specBuilder.build(request);
        Page<ApAdvanceDTO> data = advanceRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Convierte una entidad ApAdvance a su DTO de lectura.
     *
     * @param advance entidad a convertir
     * @return DTO con los datos del anticipo
     */
    private ApAdvanceDTO toDTO(ApAdvance advance) {
        return ApAdvanceDTO.builder()
                .id(advance.getId())
                .thirdPartyId(advance.getThirdParty() != null ? advance.getThirdParty().getId() : null)
                .thirdPartyName(advance.getThirdParty() != null ? advance.getThirdParty().getBusinessName() : null)
                .amount(advance.getAmount())
                .advanceDate(advance.getAdvanceDate())
                .status(advance.getStatus())
                .appliedInvoiceId(advance.getAppliedInvoiceId())
                .appliedAmount(advance.getAppliedAmount())
                .availableAmount(availableOf(advance))
                .build();
    }

    /**
     * HU-AP-05 E3 (Bloque AR): valida que la cuenta bancaria o caja del
     * anticipo tenga fondos suficientes. Replica la logica de
     * ApPaymentService.validateSufficientFunds.
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
                        "El saldo disponible en la cuenta seleccionada no es suficiente para este anticipo. "
                        + "Saldo disponible: $" + available);
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
                        "El saldo disponible en la caja seleccionada no es suficiente para este anticipo. "
                        + "Saldo disponible: $" + available);
            }
        }
    }
}
