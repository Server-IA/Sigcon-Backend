package com.sigcon.backend.accounts_receivable.payments.domain.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.accounts_receivable.payments.application.ArPaymentDTO;
import com.sigcon.backend.accounts_receivable.payments.application.CreateArPaymentRequest;
import com.sigcon.backend.accounts_receivable.payments.domain.model.ArPayment;
import com.sigcon.backend.accounts_receivable.payments.domain.repository.ArPaymentRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
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
 * Servicio para gestion de cobros y abonos a facturas de venta.
 * Cubre HUs AR-02 y AR-08.
 * Registra cobros, actualiza saldos de factura y genera asientos contables
 * (Debito Bancos / Credito CxC cliente).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArPaymentService {

    private final ArPaymentRepository paymentRepository;
    private final SalesInvoiceRepository invoiceRepository;
    private final JournalEntryService journalEntryService;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final AuditPublisher auditPublisher;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final DataTableSpecificationBuilder<ArPayment> specBuilder = new DataTableSpecificationBuilder<>();

    /**
     * Registra un cobro o abono a una factura de venta.
     * Valida la existencia de la factura, su estado, que el monto no supere
     * el saldo pendiente, unicidad de referencia y periodo contable abierto.
     * Actualiza el saldo pendiente y el estado de la factura.
     *
     * @param request datos del cobro a registrar
     * @return ResponseEntity con el cobro registrado
     * @throws IllegalArgumentException si la factura no existe, la referencia esta duplicada
     *                                  o el monto supera el saldo
     * @throws IllegalStateException    si la factura esta anulada/liquidada o el periodo cerrado
     */
    @Transactional
    public ResponseEntity<?> registerPayment(CreateArPaymentRequest request) {
        // 1. Buscar factura
        SalesInvoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada"));

        // 2. Validar estado de la factura
        if (invoice.getStatus() == SalesInvoiceStatus.VOIDED) {
            throw new IllegalStateException("No se puede registrar un cobro a una factura anulada");
        }
        if (invoice.getStatus() == SalesInvoiceStatus.SETTLED) {
            throw new IllegalStateException("No se puede registrar un cobro a una factura liquidada");
        }
        if (invoice.getStatus() == SalesInvoiceStatus.PAID) {
            throw new IllegalStateException("La factura ya se encuentra totalmente pagada");
        }
        if (invoice.getStatus() == SalesInvoiceStatus.DRAFT) {
            throw new IllegalStateException("No se puede registrar un cobro a una factura en borrador");
        }

        // 3. Validar monto no supere saldo pendiente
        BigDecimal balanceDue = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : BigDecimal.ZERO;
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto del cobro debe ser mayor a cero");
        }
        if (request.getAmount().compareTo(balanceDue) > 0) {
            // HU-AR-08 E2: mensaje literal de la HU
            throw new IllegalArgumentException(
                    "El valor del pago supera el saldo pendiente de la factura. Saldo actual: $" + balanceDue);
        }

        // 4. Validar referencia de cobro no duplicada
        if (request.getPaymentReference() != null && !request.getPaymentReference().isBlank()) {
            if (paymentRepository.existsByPaymentReferenceAndDeletedAtIsNull(request.getPaymentReference())) {
                throw new IllegalArgumentException("Ya existe un cobro con esta referencia: " + request.getPaymentReference());
            }
        }

        // HU-AR-02 E3 + HU-AR-08 E3: idempotencia por (invoice, amount, paymentDate)
        // cuando paymentReference es null. Evita doble-click.
        if (request.getPaymentReference() == null || request.getPaymentReference().isBlank()) {
            if (paymentRepository.existsByInvoice_IdAndAmountAndPaymentDateAndDeletedAtIsNull(
                    invoice.getId(), request.getAmount(), request.getPaymentDate())) {
                throw new IllegalArgumentException(
                        "Ya existe un cobro identico para esta factura (mismo monto y fecha). "
                        + "Si realmente es un pago separado, informe un paymentReference distinto.");
            }
        }

        // 5. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getPaymentDate());

        // 6. Crear el cobro
        ArPayment payment = ArPayment.builder()
                .invoice(invoice)
                .amount(request.getAmount())
                .paymentDate(request.getPaymentDate())
                .paymentReference(request.getPaymentReference())
                .paymentMethod(request.getPaymentMethod())
                .bankAccountId(request.getBankAccountId())
                .cashId(request.getCashId())
                .bankMovementId(request.getBankMovementId())
                .status("COMPLETED")
                .notes(request.getNotes())
                .build();

        payment = paymentRepository.save(payment);
        auditPublisher.publishCreate(AuditModule.AR, "ArPayment", payment.getId(), "ArPayment creado id=" + payment.getId());

        // 7. Actualizar saldo de la factura
        BigDecimal newBalance = balanceDue.subtract(request.getAmount());
        invoice.setBalanceDue(newBalance);

        if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(SalesInvoiceStatus.PAID);
        } else {
            invoice.setStatus(SalesInvoiceStatus.PARTIALLY_PAID);
        }
        invoiceRepository.save(invoice);
        auditPublisher.publishCreate(AuditModule.AR, "ArPayment", invoice.getId(), "ArPayment creado id=" + invoice.getId());

        // 8. Generar asiento contable (Debito Bancos / Credito CxC cliente)
        try {
            // AR-02: Cuentas resueltas por AccountMappingService (deuda tecnica resuelta en V31)
            // Debito: Bancos (PUC 1110) - entrada de efectivo por el cobro
            // Credito: CxC clientes (PUC 1305) - reduccion del saldo pendiente
            Long debitAccountId = accountMappingService.resolveOrThrow(AccountingConcept.BANCOS_DEFAULT);
            Long creditAccountId = accountMappingService.resolveOrThrow(AccountingConcept.AR_CLIENTES);

            String thirdPartyNit = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getNit() : null;

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                    .entryDate(request.getPaymentDate())
                    .description("Cobro factura " + invoice.getInvoiceNumber()
                            + " - Ref: " + (request.getPaymentReference() != null ? request.getPaymentReference() : "N/A"))
                    .sourceModule(JournalSourceModule.AR)
                    .sourceId(payment.getId())
                    .lines(List.of(
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(debitAccountId)
                                    .debitAmount(request.getAmount())
                                    .creditAmount(BigDecimal.ZERO)
                                    .description("Ingreso " + request.getPaymentMethod()
                                            + " factura " + invoice.getInvoiceNumber())
                                    .thirdPartyNit(thirdPartyNit)
                                    .build(),
                            CreateJournalEntryLineRequest.builder()
                                    .accountingAccountId(creditAccountId)
                                    .debitAmount(BigDecimal.ZERO)
                                    .creditAmount(request.getAmount())
                                    .description("Cobro CxC factura " + invoice.getInvoiceNumber())
                                    .thirdPartyNit(thirdPartyNit)
                                    .build()
                    ))
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(jeRequest, "sistema");
            payment.setJournalEntryId(je.getId());
            paymentRepository.save(payment);
            auditPublisher.publishCreate(AuditModule.AR, "ArPayment", payment.getId(), "ArPayment creado id=" + payment.getId());
            log.info("Asiento contable {} generado para cobro {} de factura {}",
                    je.getId(), payment.getId(), invoice.getId());
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para cobro {}: {}", payment.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar el cobro: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para cobro {}", payment.getId(), e);
            throw e;
        }

        // HU-AR-02 E4: publicar evento Spring para integracion CG/INT/AU.
        try {
            boolean partial = invoice.getStatus() == SalesInvoiceStatus.PARTIALLY_PAID;
            eventPublisher.publishEvent(new com.sigcon.backend.accounts_receivable.events
                    .ArPaymentProcessedEvent(this, payment.getId(), invoice.getId(),
                    payment.getAmount(), payment.getJournalEntryId(),
                    payment.getPaymentMethod(), partial));
        } catch (Exception ev) {
            log.warn("No se pudo publicar ArPaymentProcessedEvent: {}", ev.getMessage());
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cobro registrado exitosamente"), Optional.of(toDTO(payment))));
    }

    /**
     * Consulta cobros con paginacion y filtros DataTable.
     *
     * @param request parametros de busqueda y paginacion
     * @return listado paginado de cobros
     */
    public ResponseEntity<?> getPayments(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

        Specification<ArPayment> spec = specBuilder.build(request);
        Page<ArPaymentDTO> data = paymentRepository.findAll(spec, pageable).map(this::toDTO);

        return ResponseEntity.ok(DataTableResponse.from(data, request.getDraw()));
    }

    /**
     * Obtiene el cobro por su identificador.
     *
     * @param id identificador del cobro
     * @return ResponseEntity con el DTO del cobro
     * @throws IllegalArgumentException si no existe
     */
    public ResponseEntity<?> getById(Long id) {
        ArPayment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("El cobro no fue encontrado"));
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cobro encontrado"), Optional.of(toDTO(payment))));
    }

    /**
     * Obtiene todos los cobros asociados a una factura de venta especifica.
     *
     * @param invoiceId identificador de la factura
     * @return lista de cobros de la factura
     */
    public ResponseEntity<?> getPaymentsByInvoice(Long invoiceId) {
        List<ArPaymentDTO> payments = paymentRepository.findByInvoiceIdAndDeletedAtIsNull(invoiceId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Cobros de la factura"), Optional.of(payments)));
    }

    /**
     * Convierte una entidad ArPayment a su DTO de lectura.
     *
     * @param payment entidad a convertir
     * @return DTO con los datos del cobro
     */
    private ArPaymentDTO toDTO(ArPayment payment) {
        return ArPaymentDTO.builder()
                .id(payment.getId())
                .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
                .invoiceNumber(payment.getInvoice() != null ? payment.getInvoice().getInvoiceNumber() : null)
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentReference(payment.getPaymentReference())
                .paymentMethod(payment.getPaymentMethod())
                .bankAccountId(payment.getBankAccountId())
                .cashId(payment.getCashId())
                .bankMovementId(payment.getBankMovementId())
                .status(payment.getStatus())
                .journalEntryId(payment.getJournalEntryId())
                .notes(payment.getNotes())
                .build();
    }
}
