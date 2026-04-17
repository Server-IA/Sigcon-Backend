package com.sigcon.backend.accounts_receivable.sales_invoices.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceLineRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.application.SalesInvoiceDTO;
import com.sigcon.backend.accounts_receivable.sales_invoices.application.SalesInvoiceLineDTO;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceLine;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoiceStatus;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceLineRepository;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.model.CurrencyType;
import com.sigcon.backend.lists_accounting.types_of_currency.domain.repository.CurrencyTypeRepository;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio del modulo Cuentas por Cobrar (AR) - Facturas de Venta.
 * Cubre HUs AR-01A, AR-01B, AR-04, AR-11 y AR-13:
 * creacion, consulta, actualizacion y eliminacion de facturas
 * con calculo automatico de impuestos y retenciones (SalesTaxEngine),
 * soporte de moneda extranjera y generacion de asiento contable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesInvoiceService {

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceLineRepository salesInvoiceLineRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final PaymentFormRepository paymentFormRepository;
    private final AssetsRepository assetsRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final JournalEntryService journalEntryService;
    private final SalesTaxEngine salesTaxEngine;
    private final AccountMappingService accountMappingService;
    private final UserUtil userUtil;

    private final DataTableSpecificationBuilder<SalesInvoice> specBuilder = new DataTableSpecificationBuilder<>();

    // Codigo ISO que representa la moneda local (COP)
    private static final String LOCAL_ISO = "COP";

    /**
     * AR-01A: Crea una nueva factura de venta (FV) con sus lineas.
     * Valida tercero, periodo abierto, numeracion secuencial, moneda y tasa de cambio.
     * Calcula impuestos y retenciones con SalesTaxEngine y genera el asiento contable.
     *
     * @param request datos de la factura
     * @return factura creada con totales calculados
     */
    @Transactional
    public SalesInvoice createSalesInvoice(CreateSalesInvoiceRequest request) {
        // Validar tercero existente
        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new IllegalArgumentException("El cliente (tercero) no existe"));

        // AR-01A: validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getInvoiceDate());

        // AR-11: moneda y tasa de cambio
        CurrencyType currency = null;
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (request.getCurrencyId() != null) {
            currency = currencyTypeRepository.findByIdAndDeletedAtIsNull(request.getCurrencyId())
                    .orElseThrow(() -> new IllegalArgumentException("La moneda no existe"));
            if (!LOCAL_ISO.equalsIgnoreCase(currency.getIsoCode())) {
                if (request.getExchangeRate() == null
                        || request.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "La tasa de cambio es obligatoria para moneda extranjera.");
                }
                exchangeRate = request.getExchangeRate();
            } else {
                exchangeRate = BigDecimal.ONE;
            }
        }

        // Forma de pago (opcional)
        PaymentForms paymentForm = null;
        if (request.getPaymentFormId() != null) {
            paymentForm = paymentFormRepository.findById(request.getPaymentFormId())
                    .orElseThrow(() -> new IllegalArgumentException("La forma de pago no existe"));
        }

        // AR-01A: generar consecutivo FV-{año}{6 digitos}
        String invoiceNumber = generateInvoiceNumber(request.getInvoiceDate().getYear());

        SalesInvoice invoice = SalesInvoice.builder()
                .invoiceNumber(invoiceNumber)
                .thirdParty(thirdParty)
                .invoiceDate(request.getInvoiceDate())
                .dueDate(request.getDueDate())
                .currency(currency)
                .exchangeRate(exchangeRate)
                .paymentForm(paymentForm)
                .resolutionNumber(request.getResolutionNumber())
                .notes(request.getNotes())
                .status(SalesInvoiceStatus.ISSUED)
                .createdBy(safeUserId())
                .lines(new ArrayList<>())
                .build();

        invoice = salesInvoiceRepository.save(invoice);

        // Construir y calcular lineas
        BigDecimal subtotalTotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal withholdingTotal = BigDecimal.ZERO;

        for (CreateSalesInvoiceLineRequest lineReq : request.getLines()) {
            SalesInvoiceLine line = buildLine(invoice, lineReq);
            salesInvoiceLineRepository.save(line);

            subtotalTotal = subtotalTotal.add(line.getSubtotal());
            taxTotal = taxTotal.add(line.getTaxAmount());
            withholdingTotal = withholdingTotal.add(line.getWithholdingAmount());
        }

        BigDecimal totalAmount = subtotalTotal.add(taxTotal).subtract(withholdingTotal);

        invoice.setSubtotal(subtotalTotal);
        invoice.setTotalTax(taxTotal);
        invoice.setTotalWithholding(withholdingTotal);
        invoice.setTotalAmount(totalAmount);
        invoice.setBalanceDue(totalAmount);
        invoice = salesInvoiceRepository.save(invoice);

        // AR-01A: generar asiento contable de venta (partida doble)
        generateJournalEntry(invoice, thirdParty);

        log.info("Factura de venta {} creada: total {}", invoiceNumber, totalAmount);
        return invoice;
    }

    /**
     * Construye una linea de factura aplicando el motor tributario (SalesTaxEngine).
     */
    private SalesInvoiceLine buildLine(SalesInvoice invoice, CreateSalesInvoiceLineRequest req) {
        BigDecimal quantity = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ZERO;
        BigDecimal unitPrice = req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal discount = req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO;

        BigDecimal grossTotal = quantity.multiply(unitPrice);
        BigDecimal subtotal = grossTotal.subtract(discount);
        if (subtotal.compareTo(BigDecimal.ZERO) < 0) subtotal = BigDecimal.ZERO;

        // AR-13 + AR-04: calcular IVA y retencion sobre la base
        SalesTaxEngine.TaxCalculationResult calc = salesTaxEngine.calculate(subtotal, req.getTaxRuleIds());

        BigDecimal total = subtotal.add(calc.tax).subtract(calc.withholding);

        Assets item = null;
        if (req.getItemId() != null) {
            item = assetsRepository.findById(req.getItemId()).orElse(null);
        }

        return SalesInvoiceLine.builder()
                .invoice(invoice)
                .item(item)
                .description(req.getDescription())
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discount(discount)
                .subtotal(subtotal)
                .taxAmount(calc.tax)
                .withholdingAmount(calc.withholding)
                .total(total)
                .build();
    }

    /**
     * AR-01A: Genera un consecutivo secuencial en formato FV-{year}{6 digitos}.
     * Por ahora solo valida unicidad; integracion con resoluciones DIAN queda pendiente.
     */
    private String generateInvoiceNumber(int year) {
        String last = salesInvoiceRepository.findMaxInvoiceNumberByYear(year);
        int next = 1;
        if (last != null) {
            // formato esperado FV-YYYYNNNNNN
            String digits = last.replaceAll("[^0-9]", "");
            if (digits.length() >= 10) {
                try {
                    next = Integer.parseInt(digits.substring(4)) + 1;
                } catch (NumberFormatException ignored) {
                    next = 1;
                }
            }
        }
        String candidate = String.format("FV-%d%06d", year, next);
        // Salvaguardia: si existiera colision, avanzar
        while (salesInvoiceRepository.existsByInvoiceNumberAndDeletedAtIsNull(candidate)) {
            next++;
            candidate = String.format("FV-%d%06d", year, next);
        }
        return candidate;
    }

    /**
     * AR-01A: Genera el asiento contable de venta usando las cuentas PUC reales
     * resueltas por {@link AccountMappingService}.
     *
     * <p>Estructura del asiento (partida doble):
     * <ul>
     *   <li>Debito: CxC cliente (PUC 1305) por el total a cobrar</li>
     *   <li>Debito: Anticipo impuestos/retenciones (PUC 1355) si aplica</li>
     *   <li>Credito: Ingresos operacionales (PUC 4135) por el subtotal</li>
     *   <li>Credito: IVA generado (PUC 2408) si aplica</li>
     * </ul>
     */
    private void generateJournalEntry(SalesInvoice invoice, ThirdParty thirdParty) {
        try {
            // Resolver cuentas contables reales por concepto (AR-01A - deuda tecnica resuelta en V31)
            Long idCxcClientes = accountMappingService.resolveOrThrow(AccountingConcept.AR_CLIENTES);
            Long idRetPracticadas = accountMappingService.resolveOrThrow(
                    AccountingConcept.AR_RET_PRACTICADAS_CLIENTE);
            Long idIngresos = accountMappingService.resolveOrThrow(AccountingConcept.AR_INGRESOS);
            Long idIvaGenerado = accountMappingService.resolveOrThrow(AccountingConcept.AR_IVA_GENERADO);

            BigDecimal subtotal = nonNull(invoice.getSubtotal());
            BigDecimal totalTax = nonNull(invoice.getTotalTax());
            BigDecimal totalWithholding = nonNull(invoice.getTotalWithholding());
            BigDecimal totalAmount = nonNull(invoice.getTotalAmount());

            List<CreateJournalEntryLineRequest> jeLines = new ArrayList<>();

            // 1. Debito: CxC cliente por el total a cobrar (PUC 1305)
            jeLines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(idCxcClientes)
                    .debitAmount(totalAmount)
                    .creditAmount(BigDecimal.ZERO)
                    .description("CxC cliente " + invoice.getInvoiceNumber())
                    .thirdPartyNit(thirdParty.getNit())
                    .build());

            // 2. Debito: retenciones que el cliente nos practica (PUC 1355) si aplica
            if (totalWithholding.compareTo(BigDecimal.ZERO) > 0) {
                jeLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(idRetPracticadas)
                        .debitAmount(totalWithholding)
                        .creditAmount(BigDecimal.ZERO)
                        .description("Retenciones por cobrar " + invoice.getInvoiceNumber())
                        .thirdPartyNit(thirdParty.getNit())
                        .build());
            }

            // 3. Credito: ingresos operacionales por venta (PUC 4135)
            jeLines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(idIngresos)
                    .debitAmount(BigDecimal.ZERO)
                    .creditAmount(subtotal)
                    .description("Ingresos venta " + invoice.getInvoiceNumber())
                    .thirdPartyNit(thirdParty.getNit())
                    .build());

            // 4. Credito: IVA generado (PUC 2408) si aplica
            if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
                jeLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(idIvaGenerado)
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(totalTax)
                        .description("IVA por pagar " + invoice.getInvoiceNumber())
                        .thirdPartyNit(thirdParty.getNit())
                        .build());
            }

            CreateJournalEntryRequest req = CreateJournalEntryRequest.builder()
                    .entryDate(invoice.getInvoiceDate())
                    .description("Factura venta " + invoice.getInvoiceNumber()
                            + " - " + (thirdParty.getBusinessName() != null
                                    ? thirdParty.getBusinessName() : thirdParty.getId()))
                    .sourceModule(JournalSourceModule.AR)
                    .sourceId(invoice.getId())
                    .lines(jeLines)
                    .build();

            JournalEntryDTO je = journalEntryService.createEntry(req, "sistema");
            invoice.setJournalEntryId(je.getId());
            salesInvoiceRepository.save(invoice);
            log.info("Asiento {} creado para FV {}", je.getId(), invoice.getInvoiceNumber());
        } catch (Exception e) {
            log.warn("No se pudo generar asiento para FV {}: {}",
                    invoice.getInvoiceNumber(), e.getMessage());
        }
    }

    /**
     * AR-01B: Consulta facturas de venta con paginacion DataTable.
     */
    public ResponseEntity<?> search(DataTableRequest request) {
        if (request == null) {
            request = new DataTableRequest();
        }
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1 ? Pageable.unpaged() : PageRequest.of(page, safeLength);
        Specification<SalesInvoice> spec = specBuilder.build(request);
        Page<SalesInvoice> result = salesInvoiceRepository.findAll(spec, pageable);
        return ResponseEntity.ok(DataTableResponse.from(result.map(this::toDto), request.getDraw()));
    }

    /**
     * AR-01B: Obtiene una factura de venta por su ID.
     */
    public ResponseEntity<?> getById(Long id) {
        SalesInvoice invoice = salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La factura de venta no fue encontrada"));
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Factura encontrada"), Optional.of(toDto(invoice))));
    }

    /**
     * Actualiza campos editables de una factura (notas, forma de pago, fecha vencimiento).
     * No permite modificar facturas VOIDED, PAID ni SETTLED.
     */
    @Transactional
    public ResponseEntity<?> update(Long id, CreateSalesInvoiceRequest request) {
        SalesInvoice invoice = salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La factura de venta no fue encontrada"));

        if (invoice.getStatus() == SalesInvoiceStatus.VOIDED
                || invoice.getStatus() == SalesInvoiceStatus.PAID
                || invoice.getStatus() == SalesInvoiceStatus.SETTLED) {
            throw new IllegalStateException(
                    "No se puede modificar una factura anulada, pagada o liquidada.");
        }

        accountingPeriodService.validatePeriodOpen(
                request.getInvoiceDate() != null ? request.getInvoiceDate() : invoice.getInvoiceDate());

        if (request.getNotes() != null) invoice.setNotes(request.getNotes());
        if (request.getDueDate() != null) invoice.setDueDate(request.getDueDate());
        if (request.getResolutionNumber() != null) invoice.setResolutionNumber(request.getResolutionNumber());
        if (request.getPaymentFormId() != null) {
            PaymentForms pf = paymentFormRepository.findById(request.getPaymentFormId())
                    .orElseThrow(() -> new IllegalArgumentException("La forma de pago no existe"));
            invoice.setPaymentForm(pf);
        }

        invoice = salesInvoiceRepository.save(invoice);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Factura actualizada correctamente"), Optional.of(toDto(invoice))));
    }

    /**
     * AR-06: Anula una factura de venta emitida.
     * Cambia el estado a VOIDED y reversa el asiento contable asociado si existia.
     * Solo permite anular facturas ISSUED u OVERDUE sin pagos.
     *
     * @param id identificador de la factura
     * @return respuesta con la factura anulada
     */
    @Transactional
    public ResponseEntity<?> voidInvoice(Long id) {
        SalesInvoice invoice = salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La factura de venta no fue encontrada"));

        if (invoice.getStatus() == SalesInvoiceStatus.VOIDED) {
            throw new IllegalStateException("La factura ya se encuentra anulada.");
        }
        if (invoice.getStatus() == SalesInvoiceStatus.PAID
                || invoice.getStatus() == SalesInvoiceStatus.PARTIALLY_PAID
                || invoice.getStatus() == SalesInvoiceStatus.SETTLED) {
            throw new IllegalStateException("No se puede anular una factura con pagos registrados.");
        }

        // Validar periodo abierto de la fecha original de la factura
        accountingPeriodService.validatePeriodOpen(invoice.getInvoiceDate());

        // Reversar asiento contable si existia.
        // Si el JE aun esta en DRAFT se elimina; si esta POSTED se genera un asiento de reversion.
        if (invoice.getJournalEntryId() != null) {
            try {
                journalEntryService.deleteEntry(invoice.getJournalEntryId());
                log.info("Asiento {} en borrador eliminado al anular FV {}",
                        invoice.getJournalEntryId(), invoice.getInvoiceNumber());
            } catch (IllegalStateException draftEx) {
                // El asiento ya esta contabilizado: aplicar reversion
                try {
                    journalEntryService.reverseEntry(
                            invoice.getJournalEntryId(),
                            "Reversion por anulacion de FV " + invoice.getInvoiceNumber(),
                            "sistema");
                } catch (Exception e) {
                    log.warn("No se pudo reversar asiento {} de FV {}: {}",
                            invoice.getJournalEntryId(), invoice.getInvoiceNumber(), e.getMessage());
                }
            } catch (Exception e) {
                log.warn("No se pudo procesar asiento {} de FV {}: {}",
                        invoice.getJournalEntryId(), invoice.getInvoiceNumber(), e.getMessage());
            }
        }

        invoice.setStatus(SalesInvoiceStatus.VOIDED);
        invoice.setBalanceDue(BigDecimal.ZERO);
        invoice = salesInvoiceRepository.save(invoice);

        log.info("Factura {} anulada.", invoice.getInvoiceNumber());
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Factura anulada correctamente"), Optional.of(toDto(invoice))));
    }

    /**
     * AR-06: Actualiza el estado a OVERDUE de todas las facturas con saldo pendiente
     * y fecha de vencimiento anterior a hoy. Ejecutado por el scheduler diario.
     *
     * @return cantidad de facturas marcadas como vencidas
     */
    @Transactional
    public int updateOverdueInvoices() {
        java.time.LocalDate today = java.time.LocalDate.now();
        List<SalesInvoice> candidates = salesInvoiceRepository.findOverdueInvoices(today);
        int updated = 0;
        for (SalesInvoice inv : candidates) {
            if (inv.getStatus() != SalesInvoiceStatus.OVERDUE) {
                inv.setStatus(SalesInvoiceStatus.OVERDUE);
                salesInvoiceRepository.save(inv);
                updated++;
            }
        }
        log.info("Actualizacion de vencimiento: {} facturas marcadas como OVERDUE", updated);
        return updated;
    }

    /**
     * Elimina logicamente una factura (soft delete). Solo permite eliminar DRAFT o ISSUED sin abonos.
     */
    @Transactional
    public ResponseEntity<?> delete(Long id) {
        SalesInvoice invoice = salesInvoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La factura de venta no fue encontrada"));

        // AR-01A / DIAN: una factura emitida (ISSUED en adelante) ya tiene consecutivo
        // fiscal asignado y no puede eliminarse sin romper la secuencia DIAN.
        // Solo se permite eliminar las DRAFT (no emitidas). Para revertir una factura
        // emitida se debe usar el flujo de ANULACION (VOIDED) que genera nota credito
        // y reversa el asiento contable, preservando el consecutivo.
        if (invoice.getStatus() != SalesInvoiceStatus.DRAFT) {
            throw new IllegalStateException(
                    "No se puede eliminar una factura ya emitida (estado: " + invoice.getStatus()
                    + "). El consecutivo fiscal debe preservarse. "
                    + "Use la opcion de Anular para revertir la factura y generar nota credito.");
        }

        salesInvoiceRepository.deleteById(id);
        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Factura eliminada correctamente"), Optional.empty()));
    }

    // ==================== Helpers ====================

    private SalesInvoiceDTO toDto(SalesInvoice invoice) {
        List<SalesInvoiceLineDTO> lines = new ArrayList<>();
        List<SalesInvoiceLine> persisted = salesInvoiceLineRepository.findAllByInvoiceId(invoice.getId());
        for (SalesInvoiceLine l : persisted) {
            lines.add(SalesInvoiceLineDTO.builder()
                    .id(l.getId())
                    .itemId(l.getItem() != null ? l.getItem().getId() : null)
                    .itemName(l.getItem() != null ? l.getItem().getAssetName() : null)
                    .description(l.getDescription())
                    .quantity(l.getQuantity())
                    .unitPrice(l.getUnitPrice())
                    .discount(l.getDiscount())
                    .subtotal(l.getSubtotal())
                    .taxAmount(l.getTaxAmount())
                    .withholdingAmount(l.getWithholdingAmount())
                    .total(l.getTotal())
                    .build());
        }

        return SalesInvoiceDTO.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .thirdPartyId(invoice.getThirdParty() != null ? invoice.getThirdParty().getId() : null)
                .thirdPartyName(invoice.getThirdParty() != null ? invoice.getThirdParty().getBusinessName() : null)
                .thirdPartyNit(invoice.getThirdParty() != null ? invoice.getThirdParty().getNit() : null)
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(invoice.getDueDate())
                .currencyId(invoice.getCurrency() != null ? invoice.getCurrency().getId() : null)
                .currencyIso(invoice.getCurrency() != null ? invoice.getCurrency().getIsoCode() : null)
                .exchangeRate(invoice.getExchangeRate())
                .paymentFormId(invoice.getPaymentForm() != null ? invoice.getPaymentForm().getId() : null)
                .paymentFormName(invoice.getPaymentForm() != null ? invoice.getPaymentForm().getName() : null)
                .subtotal(invoice.getSubtotal())
                .totalTax(invoice.getTotalTax())
                .totalWithholding(invoice.getTotalWithholding())
                .totalAmount(invoice.getTotalAmount())
                .balanceDue(invoice.getBalanceDue())
                .status(invoice.getStatus())
                .notes(invoice.getNotes())
                .resolutionNumber(invoice.getResolutionNumber())
                .cufe(invoice.getCufe())
                .xmlSent(invoice.getXmlSent())
                .journalEntryId(invoice.getJournalEntryId())
                .lines(lines)
                .build();
    }

    private BigDecimal nonNull(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private Long safeUserId() {
        try {
            return userUtil.getUser().getId();
        } catch (Exception e) {
            return null;
        }
    }
}
