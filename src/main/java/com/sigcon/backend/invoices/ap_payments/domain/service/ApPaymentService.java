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

import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
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
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final ApplicationEventPublisher eventPublisher;

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

        // 5. Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getPaymentDate());

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
        } catch (Exception e) {
            log.warn("No se pudo generar asiento contable para pago {}: {}",
                    payment.getId(), e.getMessage());
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
}
