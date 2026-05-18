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
import com.sigcon.backend.third_parties.commercial_data.domain.model.CommercialData;
import com.sigcon.backend.third_parties.commercial_data.domain.repository.CommercialDataRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.UserUtil;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

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
    private final CommercialDataRepository commercialDataRepository;
    private final CurrencyTypeRepository currencyTypeRepository;
    private final PaymentFormRepository paymentFormRepository;
    private final AssetsRepository assetsRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final JournalEntryService journalEntryService;
    private final SalesTaxEngine salesTaxEngine;
    private final AccountMappingService accountMappingService;
    private final UserUtil userUtil;
    private final AuditPublisher auditPublisher;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

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

        // HU-AR-01A E3: si el contador no envia fecha de vencimiento, calcularla
        // automaticamente sumando los dias del termino de pago configurado en
        // los Datos Comerciales del cliente. Si el cliente no tiene termino de
        // pago configurado, alertar para que se complete antes de continuar.
        if (request.getDueDate() == null) {
            CommercialData commercial = commercialDataRepository
                    .findByThirdPartyIdAndDeletedAtIsNull(thirdParty.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El cliente no tiene terminos de pago configurados. "
                          + "Registre los datos comerciales del cliente antes de continuar."));
            if (commercial.getPaymentTerm() == null || commercial.getPaymentTerm().getDays() == null) {
                throw new IllegalArgumentException(
                        "El cliente no tiene terminos de pago configurados. "
                      + "Registre los datos comerciales del cliente antes de continuar.");
            }
            request.setDueDate(request.getInvoiceDate().plusDays(commercial.getPaymentTerm().getDays()));
        }

        // HU-AR-01A E3 (defecto adicional): no permitir vencimiento anterior a la
        // fecha de la factura. Generaria saldo vencido apenas se emite.
        if (request.getDueDate().isBefore(request.getInvoiceDate())) {
            throw new IllegalArgumentException(
                    "La fecha de vencimiento no puede ser anterior a la fecha de la factura.");
        }

        // AR-11: moneda y tasa de cambio
        CurrencyType currency = null;
        BigDecimal exchangeRate = BigDecimal.ONE;
        if (request.getCurrencyId() != null) {
            currency = currencyTypeRepository.findByIdAndDeletedAtIsNull(request.getCurrencyId())
                    .orElseThrow(() -> new IllegalArgumentException("La moneda no existe"));
            // HU-AR-11 E1: validar moneda activa
            if (currency.getStatus() != null
                    && !"ACTIVE".equalsIgnoreCase(currency.getStatus().name())) {
                throw new IllegalArgumentException(
                        "La moneda " + currency.getIsoCode()
                        + " no esta habilitada en Listas Contables. Active la moneda antes de usarla.");
            }
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

        // HU-AR-04 E2: la suma de retenciones no puede igualar ni superar la base
        // gravable + IVA (subtotal + tax). Si lo hace, la factura quedaria con neto
        // <= 0, lo cual es contablemente invalido. Bloquear con mensaje claro.
        BigDecimal grossBeforeWithholding = subtotalTotal.add(taxTotal);
        if (withholdingTotal.compareTo(grossBeforeWithholding) >= 0
                && grossBeforeWithholding.compareTo(BigDecimal.ZERO) > 0) {
            throw new IllegalArgumentException(
                    "El total de retenciones ($" + withholdingTotal + ") no puede ser igual ni "
                  + "superior al total de la factura ($" + grossBeforeWithholding + "). "
                  + "Revise la configuracion fiscal del cliente y de las reglas tributarias aplicadas.");
        }

        BigDecimal totalAmount = grossBeforeWithholding.subtract(withholdingTotal);

        // HU-TER-11 E5 (2026-04-27): validar limite de credito del cliente.
        // Suma cartera pendiente + el monto de la factura nueva. Si supera el
        // creditLimit configurado en commercial-data, BLOQUEA la emision con
        // mensaje claro mostrando los 3 montos. Si el cliente no tiene
        // commercial-data o creditLimit es null, no aplica el check.
        try {
            CommercialData commercial = commercialDataRepository
                    .findByThirdPartyIdAndDeletedAtIsNull(thirdParty.getId())
                    .orElse(null);
            if (commercial != null && commercial.getLimitCredit() != null
                    && commercial.getLimitCredit().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentBalance = salesInvoiceRepository
                        .sumBalanceDueByThirdParty(thirdParty.getId());
                if (currentBalance == null) currentBalance = BigDecimal.ZERO;
                BigDecimal projected = currentBalance.add(totalAmount);
                if (projected.compareTo(commercial.getLimitCredit()) > 0) {
                    throw new IllegalArgumentException(
                            "Esta factura superaria el limite de credito del cliente. "
                          + "Limite: $" + commercial.getLimitCredit() + ", "
                          + "cartera pendiente: $" + currentBalance + ", "
                          + "factura nueva: $" + totalAmount + ". "
                          + "Se requiere aprobacion previa de un supervisor.");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("No se pudo validar limite de credito para tercero {}: {}",
                    thirdParty.getId(), e.getMessage());
        }

        invoice.setSubtotal(subtotalTotal);
        invoice.setTotalTax(taxTotal);
        invoice.setTotalWithholding(withholdingTotal);
        invoice.setTotalAmount(totalAmount);
        invoice.setBalanceDue(totalAmount);
        invoice = salesInvoiceRepository.save(invoice);

        // AR-01A: generar asiento contable de venta (partida doble)
        generateJournalEntry(invoice, thirdParty);

        auditPublisher.publishCreate(AuditModule.AR, "SalesInvoice", invoice.getId(),
                "Factura de venta creada: " + invoiceNumber + " total $" + totalAmount);

        // HU-AR-01A E4: publicar evento Spring para que CG/INT/AU puedan
        // reaccionar (auditoria forensica adicional, integraciones futuras).
        // El JE ya quedo persistido sincronicamente arriba; el evento es
        // post-commit (TransactionalEventListener AFTER_COMMIT lo respeta).
        try {
            String origin = invoice.getIntegrationSource() != null
                    && invoice.getIntegrationSource().getSource() != null
                    ? invoice.getIntegrationSource().getSource().name() : "MANUAL";
            eventPublisher.publishEvent(new com.sigcon.backend.accounts_receivable.events
                    .SalesInvoiceCreatedEvent(this, invoice.getId(), invoiceNumber,
                    thirdParty != null ? thirdParty.getId() : null,
                    totalAmount, invoice.getJournalEntryId(), origin));
        } catch (Exception ev) {
            log.warn("No se pudo publicar SalesInvoiceCreatedEvent: {}", ev.getMessage());
        }

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

        // AAEF QA Bloque BJ (HU-INT-RF-04 E1, 2026-05-18): si la linea trae
        // overrides de IVA/retencion (mapper AAEF), persistir esos montos
        // directos en lugar de invocar SalesTaxEngine. Esto evita que AAEF
        // necesite resolver tax_rule_id por tenant (varia por empresa). Antes
        // las facturas AAEF con TotalVAT > 0 quedaban con total_tax=0 porque
        // no traen taxRuleIds.
        BigDecimal taxAmount;
        BigDecimal withholdingAmount;
        if (req.getTaxAmountOverride() != null || req.getWithholdingAmountOverride() != null) {
            taxAmount = req.getTaxAmountOverride() != null ? req.getTaxAmountOverride() : BigDecimal.ZERO;
            withholdingAmount = req.getWithholdingAmountOverride() != null
                    ? req.getWithholdingAmountOverride() : BigDecimal.ZERO;
        } else {
            // AR-13 + AR-04: calcular IVA y retencion sobre la base via motor tributario
            SalesTaxEngine.TaxCalculationResult calc = salesTaxEngine.calculate(subtotal, req.getTaxRuleIds());
            taxAmount = calc.tax;
            withholdingAmount = calc.withholding;
        }

        BigDecimal total = subtotal.add(taxAmount).subtract(withholdingAmount);

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
                .taxAmount(taxAmount)
                .withholdingAmount(withholdingAmount)
                .total(total)
                // AAEF v1.1: persistir overrides PUC si vienen del mapper
                .accountDebitOverride(req.getAccountDebitOverride())
                .accountCreditOverride(req.getAccountCreditOverride())
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
            Long idIngresosDefault = accountMappingService.resolveOrThrow(AccountingConcept.AR_INGRESOS);
            Long idIvaGenerado = accountMappingService.resolveOrThrow(AccountingConcept.AR_IVA_GENERADO);

            // HU-AR-04 E3 fix: cargar lineas reales desde el repo. Las lineas se
            // guardan via salesInvoiceLineRepository.save() en createSalesInvoice
            // sin asignarse al invoice.lines (que se construye con builder vacio),
            // asi que invoice.getLines() retorna empty si dependemos solo del entity.
            List<SalesInvoiceLine> lines = salesInvoiceLineRepository
                    .findAllByInvoiceId(invoice.getId());

            // AAEF v1.1: override de la cuenta de CxC si alguna linea trae uno
            if (lines != null) {
                for (SalesInvoiceLine sline : lines) {
                    if (sline.getAccountDebitOverride() != null) {
                        idCxcClientes = sline.getAccountDebitOverride();
                        break;
                    }
                }
            }

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

            // 3. Credito: ingresos operacionales por venta (PUC 4135).
            // HU-AR-04 E3: detalle por linea. Cuando la factura tiene varias lineas
            // (productos/servicios distintos), se generan multiples lineas de ingreso
            // agrupadas por (cuenta, descripcion). Si todas comparten cuenta, se
            // consolida en una sola linea para no inflar el JE innecesariamente.
            java.util.Map<Long, BigDecimal> revenueByAccount = new java.util.LinkedHashMap<>();
            java.util.Map<Long, String> revenueDescriptions = new java.util.LinkedHashMap<>();
            if (lines != null && !lines.isEmpty()) {
                for (SalesInvoiceLine sline : lines) {
                    Long acct = sline.getAccountCreditOverride() != null
                            ? sline.getAccountCreditOverride() : idIngresosDefault;
                    BigDecimal lineSubtotal = nonNull(sline.getSubtotal());
                    revenueByAccount.merge(acct, lineSubtotal, BigDecimal::add);
                    String desc = sline.getDescription() != null && !sline.getDescription().isBlank()
                            ? sline.getDescription() : ("Linea " + sline.getId());
                    revenueDescriptions.merge(acct, desc, (a, b) -> a + " | " + b);
                }
            } else {
                revenueByAccount.put(idIngresosDefault, subtotal);
                revenueDescriptions.put(idIngresosDefault,
                        "Ingresos venta " + invoice.getInvoiceNumber());
            }
            for (java.util.Map.Entry<Long, BigDecimal> e : revenueByAccount.entrySet()) {
                String descAgg = revenueDescriptions.get(e.getKey());
                String desc = descAgg != null && descAgg.length() <= 240
                        ? "Ingresos " + invoice.getInvoiceNumber() + ": " + descAgg
                        : "Ingresos venta " + invoice.getInvoiceNumber();
                jeLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(e.getKey())
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(e.getValue())
                        .description(desc)
                        .thirdPartyNit(thirdParty.getNit())
                        .build());
            }

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
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Error generando asiento contable para FV {}: {}", invoice.getInvoiceNumber(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar la factura de venta: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para FV {}", invoice.getInvoiceNumber(), e);
            throw e;
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
        auditPublisher.publishUpdate(AuditModule.AR, "SalesInvoice", invoice.getId(),
                "Factura de venta actualizada: " + invoice.getInvoiceNumber());
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
                } catch (IllegalArgumentException | IllegalStateException e) {
                    log.error("Error reversando asiento {} de FV {}: {}",
                            invoice.getJournalEntryId(), invoice.getInvoiceNumber(), e.getMessage());
                    throw new IllegalStateException(
                            "No se pudo anular la factura: " + e.getMessage(), e);
                } catch (RuntimeException e) {
                    log.error("Error inesperado reversando asiento {} de FV {}",
                            invoice.getJournalEntryId(), invoice.getInvoiceNumber(), e);
                    throw e;
                }
            } catch (RuntimeException e) {
                log.error("Error inesperado procesando asiento {} de FV {}",
                        invoice.getJournalEntryId(), invoice.getInvoiceNumber(), e);
                throw e;
            }
        }

        invoice.setStatus(SalesInvoiceStatus.VOIDED);
        invoice.setBalanceDue(BigDecimal.ZERO);
        invoice = salesInvoiceRepository.save(invoice);
        auditPublisher.publishDelete(AuditModule.AR, "SalesInvoice", invoice.getId(),
                "Factura de venta anulada: " + invoice.getInvoiceNumber());

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
                auditPublisher.publishUpdate(AuditModule.AR, "SalesInvoice", inv.getId(), "SalesInvoice actualizado id=" + inv.getId());
                updated++;
            }
        }
        log.info("Actualizacion de vencimiento: {} facturas marcadas como OVERDUE", updated);
        return updated;
    }

    /**
     * HU-AR-06 E1 + E3: pasada nocturna de RECONCILIACION integral. Recorre
     * todas las facturas no eliminadas y corrige el status segun el saldo real:
     *   - balanceDue == 0 + status != PAID/SETTLED/VOIDED → set PAID
     *   - balanceDue > 0 + status == PAID → set PARTIALLY_PAID
     *   - balanceDue > 0 + dueDate < today + status != OVERDUE → set OVERDUE
     *   - balanceDue > 0 + dueDate >= today + status == OVERDUE → set ISSUED/PARTIALLY_PAID
     * Garantiza HU-AR-06 E3: el estado siempre coincide con el saldo real.
     */
    @Transactional
    public int reconcileInvoiceStatuses() {
        java.time.LocalDate today = java.time.LocalDate.now();
        // findAll respeta @Filter del tenant si esta activo. El scheduler entra
        // como PLATFORM_ADMIN, por lo cual recorre TODAS las empresas.
        int updated = 0;
        for (SalesInvoice inv : salesInvoiceRepository.findAll()) {
            if (inv.getStatus() == SalesInvoiceStatus.DRAFT
             || inv.getStatus() == SalesInvoiceStatus.VOIDED) continue;
            BigDecimal bal = inv.getBalanceDue() != null ? inv.getBalanceDue() : BigDecimal.ZERO;
            SalesInvoiceStatus prev = inv.getStatus();
            SalesInvoiceStatus target = prev;
            BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
            if (bal.compareTo(BigDecimal.ZERO) <= 0) {
                if (prev != SalesInvoiceStatus.PAID && prev != SalesInvoiceStatus.SETTLED) {
                    target = SalesInvoiceStatus.PAID;
                }
            } else if (bal.compareTo(total) < 0) {
                if (inv.getDueDate() != null && inv.getDueDate().isBefore(today)) {
                    target = SalesInvoiceStatus.OVERDUE;
                } else if (prev == SalesInvoiceStatus.PAID || prev == SalesInvoiceStatus.OVERDUE) {
                    target = SalesInvoiceStatus.PARTIALLY_PAID;
                }
            } else { // bal == total → no se ha pagado nada
                if (inv.getDueDate() != null && inv.getDueDate().isBefore(today)) {
                    target = SalesInvoiceStatus.OVERDUE;
                } else if (prev == SalesInvoiceStatus.PAID
                        || prev == SalesInvoiceStatus.PARTIALLY_PAID) {
                    target = SalesInvoiceStatus.ISSUED;
                }
            }
            if (target != prev) {
                inv.setStatus(target);
                salesInvoiceRepository.save(inv);
                auditPublisher.publishUpdate(AuditModule.AR, "SalesInvoice", inv.getId(),
                        "Reconciliacion AR-06: status " + prev + " -> " + target
                                + " (saldo=" + bal + ")");
                updated++;
            }
        }
        log.info("Reconciliacion AR-06: {} facturas con status corregido", updated);
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
                // HU-AR-01A E6: estado fiscal DIAN visible
                .dianStatus(invoice.getDianStatus() != null ? invoice.getDianStatus().name() : null)
                .dianMessage(invoice.getDianMessage())
                // HU-AR-01B E5: origen de la factura
                .source(invoice.getIntegrationSource() != null
                        && invoice.getIntegrationSource().getSource() != null
                        ? invoice.getIntegrationSource().getSource().name() : "MANUAL")
                .externalId(invoice.getIntegrationSource() != null
                        ? invoice.getIntegrationSource().getExternalId() : null)
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
