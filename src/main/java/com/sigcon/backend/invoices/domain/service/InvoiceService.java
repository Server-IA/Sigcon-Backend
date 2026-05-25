package com.sigcon.backend.invoices.domain.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.sigcon.backend.assets.assets.application.ViewAssetsDTO;
import com.sigcon.backend.assets.assets.domain.model.Assets;
import com.sigcon.backend.assets.assets.domain.repository.AssetsRepository;
import com.sigcon.backend.general.accounting.AccountingPeriodService;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryLineRequest;
import com.sigcon.backend.general.accounting.journal.application.CreateJournalEntryRequest;
import com.sigcon.backend.general.accounting.journal.application.JournalEntryDTO;
import com.sigcon.backend.general.accounting.journal.domain.model.JournalEntry;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalEntryStatus;
import com.sigcon.backend.general.accounting.journal.domain.model.enums.JournalSourceModule;
import com.sigcon.backend.general.accounting.journal.domain.repository.JournalEntryRepository;
import com.sigcon.backend.general.accounting.journal.domain.service.JournalEntryService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.invoices.application.InvoiceDTO;
import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.application.LineInvoiceDTO;
import com.sigcon.backend.invoices.application.LineInvoiceRequestDTO;
import com.sigcon.backend.invoices.application.LineInvoiceRulerTaxRequestDTO;
import com.sigcon.backend.invoices.domain.model.InvoiceStates;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.model.LinesInvoice;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.invoices.domain.model.TypesInvoices;
import com.sigcon.backend.invoices.domain.model.enums.StatusesInvoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.invoices.domain.repository.InvoiceStateRepository;
import com.sigcon.backend.invoices.domain.repository.LineInvoiceRepository;
import com.sigcon.backend.invoices.domain.repository.TypeInvoiceRepository;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.TaxRulerEntity;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.TypeRulerTax;
import com.sigcon.backend.lists_accounting.ruler_tax.domain.repository.RuleTaxRepository;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.AccountingAccount;
import com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.service.UserService;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import com.sigcon.backend.third_parties.third_parties.domain.repository.ThirdPartyRepository;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.invoices.domain.events.ApInvoiceCreatedEvent;
import com.sigcon.backend.invoices.domain.events.ApInvoiceUpdatedEvent;
import com.sigcon.backend.invoices.domain.events.ApInvoiceDeletedEvent;
import com.sigcon.backend.utils.UserUtil;
// HU-AP-25 (Bloque AR): auditoria al anular factura.
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de facturas del modulo Cuentas por Pagar (AP).
 * Gestiona el ciclo de vida completo de facturas de compra:
 * creacion, consulta, actualizacion, eliminacion e integracion
 * con el motor contable (JournalEntryService).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final LineInvoiceRepository linesInvoiceRepository;

    private final InvoiceStateRepository invoiceStateRepository;
    private final TypeInvoiceRepository typeInvoiceRepository;
    private final PaymentFormRepository paymentFormRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final AssetsRepository assetRepository;
    private final AccountingAccountRepository accountingAccountRepository;
    private final RuleTaxRepository taxRulerRepository;

    private final JournalEntryService journalEntryService;
    // HU-AP-02 E1: acceso directo al repo de JE para sincronizar el asiento
    // asociado al editar la factura (description + entry_date) si esta en
    // estado DRAFT. Si esta POSTED, no se toca (audit trail contable).
    private final JournalEntryRepository journalEntryRepository;
    private final AccountingPeriodService accountingPeriodService;
    private final AccountMappingService accountMappingService;
    private final ApplicationEventPublisher eventPublisher;
    /** AP-03 E3: para validar conciliacion bancaria al liquidar. */
    private final com.sigcon.backend.invoices.ap_payments.domain.repository.ApPaymentRepository apPaymentRepository;
    /** HU-AP-25 (Bloque AR): publicar audit log al anular factura. */
    private final AuditPublisher auditPublisher;
    /** HU-AP-22 (Bloque AU): aislar cada fila del bulk en su propio TX. */
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    private final DataTableSpecificationBuilder<Invoices> dataTableSpecificationBuilder = new DataTableSpecificationBuilder<>();

    private final UserUtil userUtil;


    /**
     * Wrapper que crea la factura y retorna un ResponseEntity con un payload
     * minimalista (solo IDs y totales). Evita serializar la entidad Invoices o
     * el InvoiceDTO completo: ambos contienen entidades JPA (thirdParty, user,
     * paymentForms, typeInvoice...) que a su vez referencian de vuelta a la
     * factura generando ciclos infinitos en Jackson.
     */
    @Transactional
    public ResponseEntity<?> createInvoiceAndReturnDto(InvoiceFCRequestDTO invoiceFCRequestDTO, Long typeInvoiceId) {
        Invoices invoice = createInvoice(invoiceFCRequestDTO, typeInvoiceId);

        java.util.Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("id", invoice.getId());
        summary.put("resolution", invoice.getResolution());
        summary.put("resolutionInvoice", invoice.getResolutionInvoice());
        summary.put("supplierInvoiceNumber", invoice.getSupplierInvoiceNumber());
        summary.put("invoiceDate", invoice.getInvoiceDate());
        summary.put("invoiceDueDay", invoice.getInvoiceDueDay());
        summary.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);
        summary.put("totalAmount", invoice.getTotalAmount());
        summary.put("totalTax", invoice.getTotalTax());
        summary.put("totalDiscount", invoice.getTotalDiscount());
        summary.put("totalPayment", invoice.getTotalPayment());
        summary.put("balanceDue", invoice.getBalanceDue());
        summary.put("journalEntryId", invoice.getJournalEntryId());
        summary.put("thirdPartyId", invoice.getThirdParty() != null ? invoice.getThirdParty().getId() : null);

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(
                java.util.Optional.of("Factura de compra creada exitosamente."),
                java.util.Optional.of(summary)));
    }

    @Transactional
    public Invoices createInvoice(InvoiceFCRequestDTO invoiceFCRequestDTO, Long typeInvoiceId) {

        // QA Bloque AU+ HU-AP-06 E2 (2026-05-06): bloquear creacion si la
        // empresa no tiene NINGUNA regla tributaria activa. La HU exige
        // alerta explicita: "No hay reglas tributarias activas".
        long activeRules = taxRulerRepository.countByStatusAndDeletedAtIsNull(
                com.sigcon.backend.lists_accounting.ruler_tax.domain.model.enums.StatusRulerTax.ACTIVE);
        if (activeRules == 0) {
            throw new IllegalStateException(
                "No hay reglas tributarias activas en el sistema. Active al menos una regla en "
                + "Listas Contables -> Reglas Tributarias antes de registrar una factura de compra.");
        }

        Invoices invoice = new Invoices();
        TypesInvoices typeInvoice = typeInvoiceRepository.findById(typeInvoiceId).orElseThrow(
            () -> new RuntimeException("El tipo de factura no existe")
        );

        invoice.setTypeInvoice(typeInvoice);

        InvoiceStates invoiceState = getInvoiceState(typeInvoiceId, invoiceFCRequestDTO.getPaymentFormId());
        invoice.setInvoiceState(invoiceState);

        PaymentForms paymentForm = paymentFormRepository.findById(invoiceFCRequestDTO.getPaymentFormId())
            .orElseThrow(
                () -> new RuntimeException("La forma de pago no existe")
            );

        invoice.setPaymentForms(paymentForm);

        User user = userUtil.getUser();
        invoice.setUser(user);

        ThirdParty thirdParty = thirdPartyRepository.findById(invoiceFCRequestDTO.getThirdPartyId())
        .orElseThrow(
            () -> new IllegalArgumentException("El proveedor no está activo o no existe en el sistema")
        );
        // HU-AP-01 E3: Proveedor inactivo o inexistente -> mensaje exacto del Excel
        if (thirdParty.getStatus() == null
                || !"ACTIVO".equalsIgnoreCase(thirdParty.getStatus().getName())) {
            throw new IllegalArgumentException("El proveedor no está activo o no existe en el sistema");
        }
        // HU-AP-06 E3: Validar que el proveedor tenga regimen tributario asignado
        // (necesario para el calculo correcto de retenciones por motor UVT).
        if (thirdParty.getTypeRegimen() == null) {
            throw new IllegalArgumentException(
                    "Proveedor no tiene clasificación tributaria válida");
        }
        invoice.setThirdParty(thirdParty);

        // El consecutivo interno `resolution` se calcula por tipo de factura (no hardcodear typeInvoiceId=1,
        // que apuntaba a NC y colisionaba con el UNIQUE (type_invoice_id, resolution) al crear FC).
        Invoices invoiceResolution = invoiceRepository.findFirstByTypeInvoiceIdAndDeletedAtIsNullOrderByIdDesc(typeInvoiceId);

        String resolution = "1";
        if (invoiceResolution != null && invoiceResolution.getResolution() != null) {
            try {
                resolution = String.valueOf(Integer.parseInt(invoiceResolution.getResolution()) + 1);
            } catch (NumberFormatException ignored) {
                resolution = "1";
            }
        }

        invoice.setResolution(resolution);

        invoice.setResolutionInvoice(invoiceFCRequestDTO.getResolutionInvoice());

        invoice.setInvoiceDate(invoiceFCRequestDTO.getInvoiceDate());
        invoice.setInvoiceDueDay(invoiceFCRequestDTO.getInvoiceDueDay());

        // AP-07: Politica de pago desde configuracion (PaymentForms.isContado en CFG).
        //   isContado == true  -> factura creada directamente como PAID (pago al contado)
        //   isContado == false -> factura creada como PENDING (credito, requiere pago posterior)
        // Fallback: si la forma de pago no expone el flag, se asume credito (PENDING).
        Boolean isCashPayment = paymentForm != null ? paymentForm.getIsContado() : null;
        if (Boolean.TRUE.equals(isCashPayment)) {
            invoice.setStatus(StatusesInvoices.PAID);
        } else {
            invoice.setStatus(StatusesInvoices.PENDING);
        }

        invoice.setSupplierInvoiceNumber(invoiceFCRequestDTO.getSupplierInvoiceNumber());
        invoice.setNotes(invoiceFCRequestDTO.getNotes());

        // Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(invoiceFCRequestDTO.getInvoiceDate());

        // AP-01 E2 / AP-24 E5: Validar duplicidad de numero de factura del proveedor por tercero y anio
        if (invoiceFCRequestDTO.getSupplierInvoiceNumber() != null
                && !invoiceFCRequestDTO.getSupplierInvoiceNumber().isBlank()
                && invoiceFCRequestDTO.getThirdPartyId() != null
                && invoiceFCRequestDTO.getInvoiceDate() != null) {
            int fiscalYear = invoiceFCRequestDTO.getInvoiceDate().getYear();
            if (invoiceRepository.existsBySupplierInvoiceNumberAndThirdPartyAndYear(
                    invoiceFCRequestDTO.getSupplierInvoiceNumber(),
                    invoiceFCRequestDTO.getThirdPartyId(),
                    fiscalYear)) {
                throw new IllegalArgumentException(
                        "Factura ya registrada para este proveedor en el año actual.");
            }
        }

        // HU-AP-01 (Bloque AT): unicidad cross-proveedor en la empresa.
        // QA reporto que la misma empresa no debe aceptar 2 facturas con mismo
        // supplierInvoiceNumber ni mismo resolutionInvoice. Las queries usan
        // @Filter("tenantFilter") - validan dentro de la empresa actual.
        if (invoiceFCRequestDTO.getSupplierInvoiceNumber() != null
                && !invoiceFCRequestDTO.getSupplierInvoiceNumber().isBlank()
                && invoiceRepository.existsBySupplierInvoiceNumberAndDeletedAtIsNull(
                        invoiceFCRequestDTO.getSupplierInvoiceNumber())) {
            throw new IllegalArgumentException(
                    "Ya existe una factura con el numero de proveedor '"
                    + invoiceFCRequestDTO.getSupplierInvoiceNumber() + "' en esta empresa.");
        }
        if (invoiceFCRequestDTO.getResolutionInvoice() != null
                && !invoiceFCRequestDTO.getResolutionInvoice().isBlank()
                && invoiceRepository.existsByResolutionInvoiceAndDeletedAtIsNull(
                        invoiceFCRequestDTO.getResolutionInvoice())) {
            throw new IllegalArgumentException(
                    "Ya existe una factura con la resolucion DIAN '"
                    + invoiceFCRequestDTO.getResolutionInvoice() + "' en esta empresa.");
        }

        invoice = invoiceRepository.save(invoice);

        Double totalPayment = 0.0;
        Double totalAmount = 0.0;
        Double totalDiscount = 0.0;
        Double totalTax = 0.0;

        for(LineInvoiceRequestDTO lineInvoiceRequestDTO : invoiceFCRequestDTO.getLineInvoices()) {
            LinesInvoice lineInvoice = createLineInvoice(lineInvoiceRequestDTO, invoice);
            totalPayment += lineInvoice.getTotal();
            totalAmount += lineInvoice.getTotal();
            totalDiscount += lineInvoice.getDiscount();
            totalTax += lineInvoice.getTax();
        }
        invoice.setTotalPayment(totalPayment);
        invoice.setTotalAmount(totalAmount);
        invoice.setTotalDiscount(totalDiscount);
        invoice.setTotalTax(totalTax);
        invoice.setBalanceDue(totalPayment);
        invoice = invoiceRepository.save(invoice);

        // Generar asiento contable: Debito gasto/inventario, Credito CxP proveedor
        generateJournalEntry(invoice, thirdParty);

        // Publicar evento de factura creada
        try {
            eventPublisher.publishEvent(new ApInvoiceCreatedEvent(
                    this, invoice.getId(),
                    BigDecimal.valueOf(invoice.getTotalPayment()),
                    thirdParty.getId()));
        } catch (Exception e) {
            log.warn("No se pudo publicar evento ApInvoiceCreatedEvent para factura {}: {}",
                    invoice.getId(), e.getMessage());
        }

        return invoice;
    }

    /**
     * HU-AP-01 E5 / HU-INT-RF-04 E2: Crea una factura de compra originada desde un
     * lote AAEF de AgroFusion. A diferencia de {@link #createInvoice}, NO calcula
     * impuestos/retenciones desde {@code taxRulesIds} (AAEF ya trae los totales
     * pre-calculados por el orquestador). En su lugar toma los totales directos
     * y genera el JE con:
     * <ul>
     *   <li>Debito PUC 5135 (default) por subtotal</li>
     *   <li>Debito PUC 2408 (IVA descontable) por vat</li>
     *   <li>Credito PUC 2205 (CxP) por totalPayment</li>
     *   <li>Credito PUC 2365 (Retenciones) por withholdings (si aplica)</li>
     * </ul>
     *
     * @param request datos base (tercero, forma pago, fecha, lineas con cuenta contable)
     * @param typeInvoiceId id del tipo 'FC' (resuelto por el caller)
     * @param subtotal base gravable sin impuestos
     * @param vat IVA descontable
     * @param withholdings retenciones practicadas
     * @param totalPayment total neto a pagar (debe cuadrar con subtotal + vat - withholdings)
     */
    @Transactional
    public Invoices createInvoiceFromAaef(InvoiceFCRequestDTO request,
                                          Long typeInvoiceId,
                                          BigDecimal subtotal,
                                          BigDecimal vat,
                                          BigDecimal withholdings,
                                          BigDecimal totalPayment) {
        Invoices invoice = new Invoices();
        TypesInvoices typeInvoice = typeInvoiceRepository.findById(typeInvoiceId)
                .orElseThrow(() -> new RuntimeException("El tipo de factura no existe"));
        invoice.setTypeInvoice(typeInvoice);
        invoice.setInvoiceState(getInvoiceState(typeInvoiceId, request.getPaymentFormId()));

        PaymentForms paymentForm = paymentFormRepository.findById(request.getPaymentFormId())
                .orElseThrow(() -> new RuntimeException("La forma de pago no existe"));
        invoice.setPaymentForms(paymentForm);

        invoice.setUser(userUtil.getUser());

        ThirdParty thirdParty = thirdPartyRepository.findById(request.getThirdPartyId())
                .orElseThrow(() -> new RuntimeException("El tercero no existe"));
        invoice.setThirdParty(thirdParty);

        // Resolucion (consecutivo interno).
        // QA-BLOQUE-AM (2026-04-29): usa MAX numerico ignorando seeds alfanumericos
        // (ej. "FC-QA6-003" de V9-ZZC) para evitar MAPPING_ERROR "For input string" en
        // lotes AAEF entrantes. Filtra por company_id porque la query es nativa.
        Long companyIdForRes = thirdParty.getCompanyId();
        Integer maxRes = invoiceRepository.findMaxNumericResolution(typeInvoiceId, companyIdForRes);
        String resolution = String.valueOf((maxRes == null ? 0 : maxRes) + 1);
        invoice.setResolution(resolution);
        invoice.setResolutionInvoice(request.getResolutionInvoice());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setInvoiceDueDay(request.getInvoiceDueDay());
        invoice.setSupplierInvoiceNumber(request.getSupplierInvoiceNumber());
        invoice.setNotes(request.getNotes());

        // Estado inicial por forma de pago (respeta politica AP-07)
        Boolean isCash = paymentForm.getIsContado();
        invoice.setStatus(Boolean.TRUE.equals(isCash) ? StatusesInvoices.PAID : StatusesInvoices.PENDING);

        // Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(request.getInvoiceDate());

        // AP-01 E2 / AP-24 E5: duplicidad por supplierInvoiceNumber + tercero + anio
        if (request.getSupplierInvoiceNumber() != null
                && !request.getSupplierInvoiceNumber().isBlank()
                && invoiceRepository.existsBySupplierInvoiceNumberAndThirdPartyAndYear(
                        request.getSupplierInvoiceNumber(),
                        request.getThirdPartyId(),
                        request.getInvoiceDate().getYear())) {
            throw new IllegalArgumentException(
                    "Factura ya registrada para este proveedor en el año actual.");
        }

        invoice = invoiceRepository.save(invoice);

        // Crear lineas SIN aplicar reglas tributarias (AAEF ya trajo totales)
        for (LineInvoiceRequestDTO lineReq : request.getLineInvoices()) {
            LinesInvoice line = new LinesInvoice();
            line.setInvoice(invoice);
            line.setAccountingAccount(accountingAccountRepository.findById(lineReq.getAccountingAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("La cuenta contable no existe")));
            line.setDescription(lineReq.getDescription());
            line.setQuantity(lineReq.getQuantity());
            line.setPrice(lineReq.getPrice());
            line.setDiscount(0.0);
            line.setTax(0.0);
            line.setTotal(lineReq.getPrice() * lineReq.getQuantity());
            linesInvoiceRepository.save(line);
        }

        // Totales desde AAEF (sobrescriben los que calcularia la logica de reglas)
        invoice.setTotalAmount(subtotal.doubleValue());
        invoice.setTotalTax(vat.doubleValue());
        invoice.setTotalDiscount(withholdings.doubleValue());
        invoice.setTotalPayment(totalPayment.doubleValue());
        invoice.setBalanceDue(totalPayment.doubleValue());
        invoice = invoiceRepository.save(invoice);

        // Generar JE con los totales AAEF (HU-INT-RF-04 E2)
        generateJournalEntry(invoice, thirdParty);

        // Evento para consumidores CG/BNK
        try {
            eventPublisher.publishEvent(new ApInvoiceCreatedEvent(
                    this, invoice.getId(),
                    BigDecimal.valueOf(invoice.getTotalPayment()),
                    thirdParty.getId()));
        } catch (Exception e) {
            log.warn("No se pudo publicar ApInvoiceCreatedEvent AAEF factura {}: {}",
                    invoice.getId(), e.getMessage());
        }

        return invoice;
    }

    @Transactional
    public LinesInvoice createLineInvoice(LineInvoiceRequestDTO lineInvoiceRequestDTO, Invoices invoice) {
        LinesInvoice lineInvoice = new LinesInvoice();
        lineInvoice.setInvoice(invoice);

        // La linea puede referenciar un activo fijo (itemId) o una cuenta contable generica
        // (accountingAccountId + description) para servicios, insumos, materiales, etc.
        if (lineInvoiceRequestDTO.getItemId() != null) {
            lineInvoice.setAsset(assetRepository.findById(lineInvoiceRequestDTO.getItemId()).orElseThrow(
                () -> new RuntimeException("El item no existe")
            ));
            // Mantener descripcion si el cliente la envio para trazabilidad
            lineInvoice.setDescription(lineInvoiceRequestDTO.getDescription());
        } else if (lineInvoiceRequestDTO.getAccountingAccountId() != null) {
            lineInvoice.setAccountingAccount(accountingAccountRepository.findById(lineInvoiceRequestDTO.getAccountingAccountId()).orElseThrow(
                () -> new IllegalArgumentException("La cuenta contable no existe")
            ));
            lineInvoice.setDescription(lineInvoiceRequestDTO.getDescription());
        } else {
            throw new IllegalArgumentException(
                "Debe indicar un activo (itemId) o una cuenta contable (accountingAccountId)"
            );
        }

        lineInvoice.setQuantity(lineInvoiceRequestDTO.getQuantity());
        lineInvoice.setPrice(lineInvoiceRequestDTO.getPrice());

        Double total = (lineInvoiceRequestDTO.getPrice() * lineInvoiceRequestDTO.getQuantity());
        Double discount = 0.0;
        Double tax = 0.0;

        for(LineInvoiceRulerTaxRequestDTO taxRule : lineInvoiceRequestDTO.getTaxRulesIds()) {
            TaxRulerEntity taxRuleEntity = taxRulerRepository.findById(taxRule.getTaxId()).orElseThrow(
                () -> new RuntimeException("La regla tributaria no existe")
            );

            if(taxRuleEntity.getTypeRulerTax() == TypeRulerTax.TAX) {
                if(taxRule.getPercentage() != null) {
                    tax += (total * taxRule.getPercentage()) / 100;
                }else if(taxRule.getValue() != null) {
                    tax += taxRule.getValue();
                }
            }else if(taxRuleEntity.getTypeRulerTax() == TypeRulerTax.WITHHOLDING) {
                // Validacion UVT: omitir retencion si la base gravable es inferior al tope minimo
                if (taxRuleEntity.getMinAmountUvt() != null && taxRuleEntity.getUvtValueYear() != null) {
                    BigDecimal baseGravable = BigDecimal.valueOf(total);
                    BigDecimal topeMinimo = BigDecimal.valueOf(
                            taxRuleEntity.getMinAmountUvt() * taxRuleEntity.getUvtValueYear());
                    if (baseGravable.compareTo(topeMinimo) < 0) {
                        log.info("Retencion omitida para regla {}: base ${} inferior al tope minimo ${} UVT",
                                taxRuleEntity.getId(), baseGravable, topeMinimo);
                        continue;
                    }
                }
                if(taxRule.getPercentage() != null) {
                    discount += (total * taxRule.getPercentage()) / 100;
                }else if(taxRule.getValue() != null) {
                    discount += taxRule.getValue();
                }
            }
        }

        lineInvoice.setDiscount(discount);
        lineInvoice.setTax(tax);


        lineInvoice.setTotal(
            total - discount + tax
        );

        return linesInvoiceRepository.save(lineInvoice);
    }


    public ResponseEntity<?> getInvoices(DataTableRequest request) {
        int start = Math.max(0, request.getStart());
        int length = request.getLength();
        int safeLength = length <= 0 ? 20 : Math.min(length, 100);
        int page = start / safeLength;

        Pageable pageable = length == -1
            ? Pageable.unpaged()
            : PageRequest.of(page, safeLength);

        Specification<Invoices> specification = dataTableSpecificationBuilder.build(request);

        // Mapear a Map<String, Object> aplanado para evitar proxies Hibernate
        // y ciclos al serializar el listado.
        Page<Invoices> invoicesPage = invoiceRepository.findAll(specification, pageable);
        return ResponseEntity.ok(
            DataTableResponse.from(invoicesPage.map(this::toListRow), request.getDraw()));
    }

    /**
     * Convierte una factura en un resumen plano para DataTable. Solo IDs y
     * strings, sin referencias a entidades JPA (evita LazyInitialization y
     * ciclos Hibernate-Jackson).
     */
    private java.util.Map<String, Object> toListRow(Invoices invoice) {
        java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", invoice.getId());
        row.put("resolution", invoice.getResolution());
        row.put("resolutionInvoice", invoice.getResolutionInvoice());
        row.put("supplierInvoiceNumber", invoice.getSupplierInvoiceNumber());
        row.put("invoiceDate", invoice.getInvoiceDate());
        row.put("invoiceDueDay", invoice.getInvoiceDueDay());
        row.put("status", invoice.getStatus() != null ? invoice.getStatus().name() : null);
        row.put("totalAmount", invoice.getTotalAmount());
        row.put("totalTax", invoice.getTotalTax());
        row.put("totalDiscount", invoice.getTotalDiscount());
        row.put("totalPayment", invoice.getTotalPayment());
        row.put("balanceDue", invoice.getBalanceDue());
        row.put("journalEntryId", invoice.getJournalEntryId());
        // HU-AP-02 E3: exponer version optimista para que el cliente la reenvie
        // en futuros PUT y se detecten conflictos de edicion concurrente.
        row.put("version", invoice.getVersion());
        // Fallo 3 (HU-AP-25 E6, informe AgroFusion): exponer el origen (MANUAL/AAEF)
        // para que el frontend pueda mostrar el mensaje de bloqueo al intentar
        // anular manualmente una factura originada por integracion AAEF.
        row.put("source",
                invoice.getIntegrationSource() != null && invoice.getIntegrationSource().getSource() != null
                        ? invoice.getIntegrationSource().getSource().name() : "MANUAL");
        try {
            if (invoice.getThirdParty() != null) {
                row.put("thirdPartyId", invoice.getThirdParty().getId());
                row.put("thirdPartyName", invoice.getThirdParty().getBusinessName());
                row.put("thirdPartyNit", invoice.getThirdParty().getNit());
            }
        } catch (Exception ignored) { /* LazyInitializationException */ }
        try {
            if (invoice.getTypeInvoice() != null) {
                row.put("typeInvoiceId", invoice.getTypeInvoice().getId());
                row.put("typeInvoiceCode", invoice.getTypeInvoice().getCode());
            }
        } catch (Exception ignored) { /* noop */ }
        try {
            if (invoice.getPaymentForms() != null) {
                row.put("paymentFormId", invoice.getPaymentForms().getId());
                row.put("paymentFormName", invoice.getPaymentForms().getName());
            }
        } catch (Exception ignored) { /* noop */ }
        return row;
    }
    // ========================= CRUD adicional

    /**
     * Obtiene una factura por su identificador con sus lineas de detalle.
     *
     * @param id identificador de la factura
     * @return ResponseEntity con la factura o error si no se encuentra
     */
    public ResponseEntity<?> getInvoiceById(Long id) {
        Invoices invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("La factura no fue encontrada."));

        // Construir respuesta plana con cabecera + lineas (sin entidades JPA raw)
        java.util.Map<String, Object> detail = toListRow(invoice);
        java.util.List<LineInvoiceDTO> lines = new java.util.ArrayList<>();
        for (LinesInvoice li : linesInvoiceRepository.findAllByInvoiceId(invoice.getId())) {
            lines.add(toLineInvoiceDto(li));
        }
        detail.put("lineInvoices", lines);
        detail.put("notes", invoice.getNotes());

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Factura encontrada"), Optional.of(detail)));
    }

    /**
     * Actualiza una factura existente.
     * Valida que la factura no este anulada ni liquidada, y que el periodo contable este abierto.
     *
     * @param id      identificador de la factura
     * @param request datos a actualizar
     * @return ResponseEntity con la factura actualizada
     * @throws IllegalStateException    si la factura esta VOIDED o SETTLED
     * @throws IllegalArgumentException si los datos no son validos
     */
    @Transactional
    public ResponseEntity<?> updateInvoice(Long id, InvoiceFCRequestDTO request) {
        Invoices invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada."));

        // HU-AP-02 E5: Las facturas originadas por AAEF solo pueden modificarse via Pull+Diff
        if (invoice.getIntegrationSource() != null
                && invoice.getIntegrationSource().getSource() != null
                && "AAEF".equals(invoice.getIntegrationSource().getSource().name())) {
            throw new IllegalStateException(
                "Las facturas originadas por AAEF solo pueden modificarse vía Pull+Diff desde AgroFusion");
        }

        // HU-AP-02 (Bloque AT): facturas PAID/SETTLED/VOIDED son inmutables.
        // QA reporto: factura totalmente pagada NO debe permitir edicion.
        // Pago parcial si permite editar campos no contables (notas, fecha venc).
        if (invoice.getStatus() == StatusesInvoices.VOIDED
                || invoice.getStatus() == StatusesInvoices.SETTLED
                || invoice.getStatus() == StatusesInvoices.PAID) {
            throw new IllegalStateException(
                "No se puede modificar una factura " +
                (invoice.getStatus() == StatusesInvoices.PAID ? "totalmente pagada" :
                 invoice.getStatus() == StatusesInvoices.VOIDED ? "anulada" : "liquidada") +
                ". El estado actual no permite ediciones.");
        }

        // HU-AP-02 E3: chequeo manual de version optimista. Hibernate solo
        // dispara OptimisticLockException cuando la entidad esta detached y se
        // hace merge - no cuando es managed via findById() y solo cambias
        // setters. Asi que comparamos manualmente y lanzamos la excepcion
        // estandar para que el GlobalExceptionHandler la traduzca a HTTP 409.
        if (request.getVersion() != null
                && invoice.getVersion() != null
                && !request.getVersion().equals(invoice.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                    "Invoices", invoice.getId());
        }

        // Validar periodo contable abierto
        accountingPeriodService.validatePeriodOpen(
            request.getInvoiceDate() != null ? request.getInvoiceDate() : invoice.getInvoiceDate());

        // QA-BLOQUE-AY HU-AP-02 E2 (2026-05-05): si la factura ya tiene asiento
        // contable POSTED, NO permitir modificar fecha de emision ni resolucion
        // (ambos cambian datos contables del JE). El usuario debe usar
        // correccion via comprobante de ajuste (HU-CG-07B). Mensajes literales HU.
        boolean jePosted = false;
        if (invoice.getJournalEntryId() != null) {
            JournalEntry je0 = journalEntryRepository.findById(invoice.getJournalEntryId()).orElse(null);
            if (je0 != null && je0.getStatus() == JournalEntryStatus.POSTED) {
                jePosted = true;
            }
        }
        if (jePosted) {
            if (request.getInvoiceDate() != null
                    && !request.getInvoiceDate().equals(invoice.getInvoiceDate())) {
                throw new IllegalStateException(
                    "No se puede modificar la fecha de emision porque la factura ya fue contabilizada en Contabilidad General. "
                    + "Use una correccion contable (ajuste) en lugar de editar la factura.");
            }
            if (request.getResolutionInvoice() != null
                    && !request.getResolutionInvoice().equals(invoice.getResolutionInvoice())) {
                throw new IllegalStateException(
                    "No se puede modificar la resolucion DIAN porque la factura ya fue contabilizada. "
                    + "Use una correccion contable (ajuste) en lugar de editar la factura.");
            }
        }

        // QA-BLOQUE-AY HU-AP-02 E3 (2026-05-05): si la factura esta en
        // PARTIALLY_PAID, solo se permiten editar campos no contables:
        //   - invoiceDueDay (dias de credito)
        //   - paymentFormId (forma de pago)
        //   - notes (observaciones)
        // Bloquear cualquier intento de cambiar monto, lineas, impuestos,
        // proveedor, numero factura, fecha emision o resolucion.
        boolean partial = invoice.getStatus() == StatusesInvoices.PARTIALLY_PAID;
        if (partial) {
            if (request.getSupplierInvoiceNumber() != null
                    && !request.getSupplierInvoiceNumber().equals(invoice.getSupplierInvoiceNumber())) {
                throw new IllegalStateException(
                    "Una factura parcialmente pagada solo permite modificar dias/fecha de vencimiento y forma de pago. "
                    + "El numero de factura no puede cambiarse.");
            }
            if (request.getInvoiceDate() != null
                    && !request.getInvoiceDate().equals(invoice.getInvoiceDate())) {
                throw new IllegalStateException(
                    "Una factura parcialmente pagada solo permite modificar dias/fecha de vencimiento y forma de pago. "
                    + "La fecha de emision no puede cambiarse.");
            }
            if (request.getResolutionInvoice() != null
                    && !request.getResolutionInvoice().equals(invoice.getResolutionInvoice())) {
                throw new IllegalStateException(
                    "Una factura parcialmente pagada solo permite modificar dias/fecha de vencimiento y forma de pago. "
                    + "La resolucion DIAN no puede cambiarse.");
            }
            if (request.getLineInvoices() != null && !request.getLineInvoices().isEmpty()) {
                throw new IllegalStateException(
                    "Una factura parcialmente pagada solo permite modificar dias/fecha de vencimiento y forma de pago. "
                    + "Las lineas/monto/impuestos no pueden cambiarse.");
            }
        }

        // Actualizar campos editables
        if (request.getSupplierInvoiceNumber() != null && !partial) {
            invoice.setSupplierInvoiceNumber(request.getSupplierInvoiceNumber());
        }
        if (request.getNotes() != null) {
            invoice.setNotes(request.getNotes());
        }
        if (request.getPaymentFormId() != null) {
            PaymentForms paymentForm = paymentFormRepository.findById(request.getPaymentFormId())
                .orElseThrow(() -> new IllegalArgumentException("La forma de pago no existe"));
            invoice.setPaymentForms(paymentForm);
        }
        if (request.getInvoiceDate() != null && !partial && !jePosted) {
            invoice.setInvoiceDate(request.getInvoiceDate());
        }
        if (request.getInvoiceDueDay() != null) {
            invoice.setInvoiceDueDay(request.getInvoiceDueDay());
        }
        if (request.getResolutionInvoice() != null && !partial && !jePosted) {
            invoice.setResolutionInvoice(request.getResolutionInvoice());
        }

        // HU-AP-02 (Bloque AT): permitir editar lineas/monto SOLO en PENDING
        // sin pagos. Si la factura tiene pagos parciales, los montos quedan
        // bloqueados (la conciliacion contable se rompe). El frontend valida
        // tambien pero el backend es la fuente de verdad.
        boolean linesProvided = request.getLineInvoices() != null
                && !request.getLineInvoices().isEmpty();
        boolean canEditLines = invoice.getStatus() == StatusesInvoices.PENDING
                && (invoice.getBalanceDue() == null
                    || invoice.getBalanceDue().equals(invoice.getTotalPayment()));

        if (linesProvided && !canEditLines) {
            throw new IllegalStateException(
                "No se pueden modificar las lineas/monto de una factura con pagos aplicados. "
                + "Anule la factura y cree una nueva, o use Notas Credito/Debito.");
        }

        if (linesProvided && canEditLines) {
            // Eliminar lineas anteriores y reversar JE viejo si DRAFT
            List<LinesInvoice> oldLines = linesInvoiceRepository.findAllByInvoiceId(invoice.getId());
            for (LinesInvoice old : oldLines) {
                linesInvoiceRepository.delete(old);
            }
            // QA Bloque AU+ HU-AP-13 E2 (2026-05-06): si el JE viejo esta POSTED y
            // se editan lineas/monto, REVERSAR el JE viejo (queda REVERSED + crea
            // contrapartida REV-) y luego regenerar el JE nuevo en DRAFT. Antes
            // solo se borraba el JE DRAFT y se dejaba intacto el POSTED, lo que
            // generaba dos JE para la misma factura sin trazabilidad de reversion.
            if (invoice.getJournalEntryId() != null) {
                JournalEntry oldJe = journalEntryRepository.findById(invoice.getJournalEntryId()).orElse(null);
                if (oldJe != null) {
                    if (oldJe.getStatus() == JournalEntryStatus.DRAFT) {
                        journalEntryService.deleteEntry(oldJe.getId());
                    } else if (oldJe.getStatus() == JournalEntryStatus.POSTED) {
                        try {
                            journalEntryService.reverseEntry(oldJe.getId(),
                                "Reversion automatica por edicion de lineas factura AP "
                                + invoice.getResolutionInvoice() + " (HU-AP-13 E2)",
                                "sistema");
                            log.info("HU-AP-13 E2: JE {} POSTED reversado por edicion lineas factura {}",
                                oldJe.getId(), invoice.getId());
                        } catch (RuntimeException revEx) {
                            log.warn("HU-AP-13 E2: no se pudo reversar JE {} de factura {}: {}",
                                oldJe.getId(), invoice.getId(), revEx.getMessage());
                            throw new IllegalStateException(
                                "No se pudo reversar el comprobante contable original: " + revEx.getMessage());
                        }
                    }
                    invoice.setJournalEntryId(null);
                }
            }

            // QA-BLOQUE-AY HU-AP-02 E1 (2026-05-05): re-crear lineas usando
            // `createLineInvoice` para que aplique correctamente las reglas
            // tributarias (TAX/WITHHOLDING) provistas en cada linea, en lugar
            // de descartarlas. Antes el update reescribia con discount=0/tax=0
            // perdiendo la configuracion fiscal.
            Double totalAmount = 0.0;
            Double totalDiscount = 0.0;
            Double totalTax = 0.0;
            for (LineInvoiceRequestDTO lineReq : request.getLineInvoices()) {
                if (lineReq.getTaxRulesIds() == null) {
                    lineReq.setTaxRulesIds(java.util.Collections.emptyList());
                }
                LinesInvoice savedLine = createLineInvoice(lineReq, invoice);
                Double subtotal = (savedLine.getPrice() != null ? savedLine.getPrice() : 0.0)
                        * (savedLine.getQuantity() != null ? savedLine.getQuantity() : 0.0);
                totalAmount += subtotal;
                totalDiscount += savedLine.getDiscount() != null ? savedLine.getDiscount() : 0.0;
                totalTax += savedLine.getTax() != null ? savedLine.getTax() : 0.0;
            }
            invoice.setTotalAmount(totalAmount);
            invoice.setTotalDiscount(totalDiscount);
            invoice.setTotalTax(totalTax);
            invoice.setTotalPayment(totalAmount + totalTax - totalDiscount);
            invoice.setBalanceDue(totalAmount + totalTax - totalDiscount);

            log.info("HU-AP-02 E1 (Bloque AY): factura {} editada con nuevas lineas + taxRulesIds. "
                    + "Total recalculado: subtotal={} tax={} ret={} payment={}",
                    invoice.getId(), totalAmount, totalTax, totalDiscount, invoice.getTotalPayment());
        }

        invoice = invoiceRepository.save(invoice);

        // Si se reemplazaron lineas, regenerar JE
        if (linesProvided && canEditLines) {
            try {
                generateJournalEntry(invoice, invoice.getThirdParty());
                log.info("HU-AP-02 (Bloque AT): JE regenerado tras editar lineas factura {}", invoice.getId());
            } catch (Exception genEx) {
                log.warn("HU-AP-02 (Bloque AT): no se pudo regenerar JE tras editar lineas: {}", genEx.getMessage());
            }
        }

        // HU-AP-02 E1: sincronizar JE asociado en CG cuando esta en DRAFT.
        // Reglas:
        //   - Si la factura tiene journalEntryId Y el JE esta en DRAFT:
        //     actualizar description (con nueva resolucion + nombre tercero) y
        //     entry_date (con nueva fecha de factura). Es la edicion natural
        //     porque el comprobante aun no se contabilizo.
        //   - Si el JE esta en POSTED o REVERSED: NO se toca por integridad
        //     contable (audit trail). El usuario debe hacer correccion via
        //     comprobante de ajuste (HU-CG-07B).
        boolean jeSynced = false;
        boolean jePostedSkipped = false;
        if (invoice.getJournalEntryId() != null) {
            try {
                JournalEntry je = journalEntryRepository.findById(invoice.getJournalEntryId())
                        .orElse(null);
                if (je != null) {
                    if (je.getStatus() == JournalEntryStatus.DRAFT) {
                        String newDesc = "Factura compra " + invoice.getResolutionInvoice()
                                + " - " + (invoice.getThirdParty() != null
                                        && invoice.getThirdParty().getBusinessName() != null
                                                ? invoice.getThirdParty().getBusinessName()
                                                : "tercero #" + (invoice.getThirdParty() != null
                                                        ? invoice.getThirdParty().getId() : "-"));
                        je.setDescription(newDesc);
                        if (invoice.getInvoiceDate() != null) {
                            je.setEntryDate(invoice.getInvoiceDate());
                            je.setPeriodYear(invoice.getInvoiceDate().getYear());
                            je.setPeriodMonth(invoice.getInvoiceDate().getMonthValue());
                        }
                        journalEntryRepository.save(je);
                        jeSynced = true;
                        log.info("HU-AP-02 E1: JE {} sincronizado (DRAFT) tras update factura {}",
                                je.getId(), invoice.getId());
                    } else if (je.getStatus() == JournalEntryStatus.POSTED) {
                        // HU-AP-13 E2 (Bloque AS): JE POSTED es inmutable. Si la
                        // factura cambio en datos contables (fecha o resolucion),
                        // diferimos la generacion del asiento de correccion DRAFT
                        // a un evento AFTER_COMMIT publicado por el bean. Esto
                        // evita rollback-only de la TX padre cuando createCorrection
                        // lanza alguna IllegalStateException.
                        boolean dateChanged = request.getInvoiceDate() != null
                                && !request.getInvoiceDate().equals(je.getEntryDate());
                        boolean resolutionChanged = request.getResolutionInvoice() != null
                                && !request.getResolutionInvoice().equals(invoice.getResolutionInvoice());
                        if (dateChanged || resolutionChanged) {
                            // Marcar para que el listener AFTER_COMMIT lo procese
                            jeSynced = true;
                            try {
                                eventPublisher.publishEvent(new com.sigcon.backend.invoices.domain.events.ApInvoicePostedEditedEvent(
                                        this, invoice.getId(), je.getId(), invoice.getResolutionInvoice(),
                                        invoice.getInvoiceDate()));
                            } catch (Exception evtEx) {
                                log.warn("HU-AP-13 E2: no se pudo encolar evento de ajuste contable: {}",
                                        evtEx.getMessage());
                            }
                            log.info("HU-AP-13 E2: factura {} con JE {} POSTED editada en datos contables. "
                                    + "Ajuste DRAFT se generara post-commit.", invoice.getId(), je.getId());
                        } else {
                            // Cambio no contable, no requiere ajuste
                            jePostedSkipped = true;
                            log.info("HU-AP-13 E2: factura {} editada en campos no contables. JE {} POSTED preservado.",
                                    invoice.getId(), je.getId());
                        }
                    } else {
                        // REVERSED: no tocar
                        jePostedSkipped = true;
                        log.info("HU-AP-13 E2: JE {} en estado {} no requiere accion tras update factura {}",
                                je.getId(), je.getStatus(), invoice.getId());
                    }
                }
            } catch (RuntimeException syncEx) {
                // No bloquear el update por una falla de sync. Logueamos y seguimos.
                log.warn("HU-AP-02 E1: no se pudo sincronizar JE de factura {}: {}",
                        invoice.getId(), syncEx.getMessage());
            }
        }

        // AP-14: publicar evento de actualizacion para consumidores (CG, BNK)
        try {
            eventPublisher.publishEvent(new ApInvoiceUpdatedEvent(
                    this, invoice.getId(),
                    invoice.getTotalPayment() != null
                            ? java.math.BigDecimal.valueOf(invoice.getTotalPayment())
                            : java.math.BigDecimal.ZERO,
                    invoice.getThirdParty() != null ? invoice.getThirdParty().getId() : null));
        } catch (Exception e) {
            log.warn("No se pudo publicar evento ApInvoiceUpdatedEvent para factura {}: {}",
                    invoice.getId(), e.getMessage());
        }

        // HU-AP-02 E1: mensaje contextual segun el resultado del sync.
        String successMsg = "Factura actualizada exitosamente";
        if (jeSynced) {
            successMsg += ". El comprobante contable asociado se sincronizo.";
        } else if (jePostedSkipped) {
            successMsg += ". El comprobante contable ya esta contabilizado y no se "
                    + "sincronizo automaticamente; use correccion via ajuste contable.";
        }
        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(
                Optional.of(successMsg), Optional.of(toListRow(invoice))));
    }

    /**
     * Elimina logicamente una factura (soft delete).
     * Solo permite eliminar facturas en estado PENDING.
     *
     * @param id identificador de la factura
     * @return ResponseEntity con mensaje de exito
     * @throws IllegalStateException    si la factura no esta en estado PENDING
     * @throws IllegalArgumentException si la factura no existe
     */
    @Transactional
    public ResponseEntity<?> deleteInvoice(Long id) {
        Invoices invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada."));

        // HU-AP-02 E5: Las facturas originadas por AAEF solo pueden eliminarse via Pull+Diff
        if (invoice.getIntegrationSource() != null
                && invoice.getIntegrationSource().getSource() != null
                && "AAEF".equals(invoice.getIntegrationSource().getSource().name())) {
            throw new IllegalStateException(
                "Las facturas originadas por AAEF solo pueden modificarse vía Pull+Diff desde AgroFusion");
        }

        // Solo se pueden eliminar facturas pendientes
        if (invoice.getStatus() != StatusesInvoices.PENDING) {
            throw new IllegalStateException("Solo se pueden eliminar facturas en estado pendiente.");
        }

        Long thirdPartyId = invoice.getThirdParty() != null ? invoice.getThirdParty().getId() : null;
        invoiceRepository.deleteById(id);

        // AP-14: publicar evento de eliminacion para consumidores
        try {
            eventPublisher.publishEvent(new ApInvoiceDeletedEvent(this, id, thirdPartyId));
        } catch (Exception e) {
            log.warn("No se pudo publicar evento ApInvoiceDeletedEvent para factura {}: {}",
                    id, e.getMessage());
        }

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Factura eliminada exitosamente"), Optional.empty()));
    }

    /**
     * AP-03: Liquida una cuenta por pagar cuando el saldo pendiente es cero.
     * Valida que balanceDue == 0 y cambia estado a SETTLED.
     *
     * @param id ID de la factura a liquidar
     * @return respuesta con la factura liquidada o error si tiene saldo pendiente
     */
    @Transactional
    public ResponseEntity<?> settleInvoice(Long id) {
        Invoices invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada."));

        // AP-03 E2: No se puede liquidar si tiene saldo pendiente
        if (invoice.getBalanceDue() != null && invoice.getBalanceDue() > 0) {
            throw new IllegalStateException("La cuenta no puede liquidarse porque aún tiene saldo.");
        }

        // AP-03 E3: validar que TODOS los pagos esten conciliados con BNK
        // (reconciledAt no null y bankMovementId != null)
        java.util.List<com.sigcon.backend.invoices.ap_payments.domain.model.ApPayment> payments =
                apPaymentRepository.findByInvoiceIdAndDeletedAtIsNull(id);
        boolean anyUnreconciled = payments.stream()
                .anyMatch(p -> p.getReconciledAt() == null || p.getBankMovementId() == null);
        if (anyUnreconciled) {
            throw new IllegalStateException(
                    "El proceso no puede continuar, pagos no conciliados en BNK");
        }

        // No se puede liquidar si ya está anulada o ya liquidada
        if (invoice.getStatus() == StatusesInvoices.VOIDED) {
            throw new IllegalStateException("No se puede liquidar una factura anulada.");
        }
        if (invoice.getStatus() == StatusesInvoices.SETTLED) {
            throw new IllegalStateException("La factura ya se encuentra liquidada.");
        }

        invoice.setStatus(StatusesInvoices.SETTLED);
        invoiceRepository.save(invoice);

        // QA Bloque AU+ HU-AP-03 E1 (2026-05-07): generar comprobante contable
        // de cierre de la obligacion. Aclaracion del QA: la HU exige ver el
        // asiento en /contabilidad/comprobantes, no solo audit + notification.
        // Como contablemente la deuda ya fue saldada por los pagos individuales
        // (D CxP / C Bancos), el JE de cierre es un asiento "memoria" que se
        // auto-cancela en la misma cuenta CxP por el monto total facturado:
        //   D 2205 (CxP) = totalPayment
        //   C 2205 (CxP) = totalPayment
        // Total D = Total C (partida doble OK), efecto contable neto = 0,
        // pero queda un comprobante POSTED con descripcion clara del cierre.
        Long settleJeId = null;
        try {
            BigDecimal totalPayment = invoice.getTotalPayment() != null
                    ? BigDecimal.valueOf(invoice.getTotalPayment()) : BigDecimal.ZERO;
            if (totalPayment.signum() > 0) {
                Long idCxpProveedores = accountMappingService.resolveOrThrow(AccountingConcept.AP_PROVEEDORES);
                String thirdPartyNit = invoice.getThirdParty() != null
                        ? invoice.getThirdParty().getNit() : null;
                String thirdPartyName = invoice.getThirdParty() != null
                        ? invoice.getThirdParty().getBusinessName() : "?";

                java.util.List<CreateJournalEntryLineRequest> closingLines = new java.util.ArrayList<>();
                closingLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(idCxpProveedores)
                        .debitAmount(totalPayment)
                        .creditAmount(BigDecimal.ZERO)
                        .description("Cierre CxP " + invoice.getResolutionInvoice() + " - Deuda saldada")
                        .thirdPartyNit(thirdPartyNit)
                        .build());
                closingLines.add(CreateJournalEntryLineRequest.builder()
                        .accountingAccountId(idCxpProveedores)
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(totalPayment)
                        .description("Cierre CxP " + invoice.getResolutionInvoice() + " - Cancelacion")
                        .thirdPartyNit(thirdPartyNit)
                        .build());

                CreateJournalEntryRequest closingReq = CreateJournalEntryRequest.builder()
                        .entryDate(java.time.LocalDate.now())
                        .description("LIQUIDACION factura " + invoice.getResolutionInvoice()
                                + " - " + thirdPartyName + " - Deuda saldada $" + totalPayment)
                        .sourceModule(JournalSourceModule.AP)
                        .sourceId(invoice.getId())
                        .lines(closingLines)
                        .build();

                var je = journalEntryService.createEntry(closingReq, "sistema");
                settleJeId = je.getId();
                // Contabilizar (DRAFT -> POSTED) para que aparezca como asiento
                // formal en CG comprobantes.
                try {
                    journalEntryService.postEntry(settleJeId);
                } catch (RuntimeException postEx) {
                    log.warn("HU-AP-03 E1: JE {} de cierre quedo en DRAFT por: {}",
                            settleJeId, postEx.getMessage());
                }
                log.info("HU-AP-03 E1: comprobante de cierre {} generado para factura {}",
                        settleJeId, invoice.getId());
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // No fallamos el settle si la generacion del comprobante de cierre
            // falla (ej. periodo cerrado, mapeo faltante). El estado SETTLED ya
            // se persistio. Loguamos para revision manual.
            log.warn("HU-AP-03 E1: no se pudo generar comprobante de cierre para factura {}: {}",
                    invoice.getId(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("HU-AP-03 E1: error inesperado al generar comprobante de cierre para factura {}",
                    invoice.getId(), ex);
        }
        // Capturar el ID del comprobante de cierre para devolverlo en la
        // respuesta y para que la auditoria lo enlace.
        final Long closingJeIdFinal = settleJeId;

        // QA-BLOQUE-AY HU-AP-03 E1 (2026-05-06): registrar en auditoria el cierre
        // de la obligacion y notificar a CG para que refleje el saldo cero del
        // proveedor. Los reportes QA de prod indicaron que el settle no
        // generaba traza ni evento downstream.
        try {
            String thirdPartyName = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getBusinessName() : "?";
            auditPublisher.publishUpdate(AuditModule.AP, "Invoice", invoice.getId(),
                    "Factura LIQUIDADA (SETTLED) - " + invoice.getResolutionInvoice()
                    + " | proveedor=" + thirdPartyName
                    + " | total=" + invoice.getTotalPayment());
        } catch (RuntimeException ex) {
            log.warn("No se pudo registrar audit log de settle para factura {}: {}", id, ex.getMessage());
        }
        try {
            Long thirdPartyId = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getId() : null;
            BigDecimal total = invoice.getTotalPayment() != null
                    ? BigDecimal.valueOf(invoice.getTotalPayment()) : BigDecimal.ZERO;
            // ApInvoiceUpdatedEvent es consumido por el listener de CG/AAEF
            // para reflejar el cambio de estado.
            eventPublisher.publishEvent(new ApInvoiceUpdatedEvent(this, invoice.getId(),
                    total, thirdPartyId));

            // QA Bloque AU+ HU-AP-03 E1 (2026-05-06): evento dedicado al cierre
            // formal para que CG registre la deuda saldada en su propia bitacora.
            String thirdPartyName = invoice.getThirdParty() != null
                    ? invoice.getThirdParty().getBusinessName() : null;
            eventPublisher.publishEvent(new com.sigcon.backend.invoices.domain.events.ApInvoiceSettledEvent(
                    this, invoice.getId(), invoice.getResolutionInvoice(),
                    thirdPartyName, thirdPartyId, total));
        } catch (RuntimeException ex) {
            log.warn("No se pudo publicar eventos en settle {}: {}", id, ex.getMessage());
        }

        // Bloque AS: payload minimo para evitar serializar proxies JPA.
        java.util.Map<String, Object> minimal = new java.util.HashMap<>();
        minimal.put("id", invoice.getId());
        minimal.put("status", "SETTLED");
        // HU-AP-03 E1: incluir id del comprobante de cierre para que el
        // frontend pueda redirigir/destacar el JE en /contabilidad/comprobantes.
        if (closingJeIdFinal != null) {
            minimal.put("closingJournalEntryId", closingJeIdFinal);
        }
        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Factura liquidada exitosamente."), Optional.of(minimal)));
    }

    /**
     * HU-AP-25 (Bloque AR): anula una factura de compra registrada.
     *
     * <p>Reglas de negocio (escenarios E1..E9):
     * <ul>
     *   <li>E1: PENDIENTE sin pagos puede anularse correctamente</li>
     *   <li>E2: motivo obligatorio (validado en DTO con @NotBlank @Size min=10)</li>
     *   <li>E3: PAGADA bloqueada → mensaje literal HU</li>
     *   <li>E4: PARCIALMENTE_PAGADA bloqueada → debe reversar pagos primero</li>
     *   <li>E5: periodo cerrado bloqueada → ajuste en periodo vigente</li>
     *   <li>E6: source=AAEF bloqueada → solo se anula via Pull+Diff</li>
     *   <li>E7: ya VOIDED → idempotente con mensaje informativo</li>
     *   <li>E8: permisos en controller (@PreAuthorize)</li>
     *   <li>E9: conserva adjuntos, historial, JE vinculado y motivo de anulacion</li>
     * </ul>
     *
     * <p>Comportamiento contable: si la factura tiene JE vinculado, se intenta
     * reverse para mantener inmutabilidad. El estado pasa a VOIDED (no se
     * elimina fisicamente para preservar trazabilidad fiscal).
     *
     * @param id      ID de la factura a anular
     * @param reason  motivo de la anulacion (min 10 chars)
     */
    @Transactional
    public ResponseEntity<?> voidInvoice(Long id, String reason) {
        Invoices invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("La factura no fue encontrada."));

        // E2: motivo obligatorio (defensa adicional al @Size del DTO)
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException(
                "Debe ingresar el motivo de anulación para continuar (mínimo 10 caracteres)");
        }

        // E7: idempotencia (payload minimo para evitar serializar proxies JPA)
        if (invoice.getStatus() == StatusesInvoices.VOIDED) {
            java.util.Map<String, Object> idem = new java.util.HashMap<>();
            idem.put("id", invoice.getId());
            idem.put("status", "VOIDED");
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("La factura ya se encuentra anulada"), Optional.of(idem)));
        }

        // E6: AAEF bloqueada
        if (invoice.getIntegrationSource() != null
                && invoice.getIntegrationSource().getSource() != null
                && "AAEF".equals(invoice.getIntegrationSource().getSource().name())) {
            throw new IllegalStateException(
                "Las facturas originadas por integración AAEF solo pueden anularse o corregirse "
                + "mediante el proceso de corrección de AgroFusion");
        }

        // E3: PAGADA bloqueada
        if (invoice.getStatus() == StatusesInvoices.PAID
                || invoice.getStatus() == StatusesInvoices.SETTLED) {
            throw new IllegalStateException(
                "Esta factura ya fue pagada y no puede anularse directamente. "
                + "Primero debe realizarse el proceso de reversión o ajuste contable");
        }

        // E4: PARCIALMENTE_PAGADA bloqueada SOLO si todavia hay pagos efectivos.
        // QA-BLOQUE-AY HU-AP-25 E4 (2026-05-05): si el contador ya reverso/anulo
        // los pagos parciales, la factura debe poder anularse aunque su status
        // diga PARTIALLY_PAID.
        // QA Bloque AU+ (2026-05-06): la deteccion previa miraba balanceDue vs
        // totalPayment, pero esos campos NO se actualizan cuando solo se reversa
        // el JE del pago (apPayments queda intacto). Ahora chequeamos los pagos
        // y consideramos "efectivos" SOLO los que tengan JE en POSTED. Si todos
        // los pagos tienen JE REVERSED/eliminado, no hay impacto contable
        // pendiente y la factura se puede anular.
        if (invoice.getStatus() == StatusesInvoices.PARTIALLY_PAID) {
            java.util.List<com.sigcon.backend.invoices.ap_payments.domain.model.ApPayment> activePayments =
                    apPaymentRepository.findByInvoiceIdAndDeletedAtIsNull(id);
            boolean hasEffectivePayments = false;
            for (var pay : activePayments) {
                if (pay.getJournalEntryId() == null) {
                    // Pago sin JE asociado: tratar como efectivo (no podemos
                    // confirmar reversion sin JE).
                    hasEffectivePayments = true;
                    break;
                }
                JournalEntry payJe = journalEntryRepository.findById(pay.getJournalEntryId()).orElse(null);
                // El pago se considera EFECTIVO si su JE existe y NO esta
                // REVERSED. Tanto DRAFT como POSTED reflejan compromiso contable
                // pendiente. Solo cuando el contador hace reverse (queda
                // REVERSED) el pago deja de impactar y la factura se puede
                // anular.
                if (payJe != null && payJe.getStatus() != JournalEntryStatus.REVERSED) {
                    hasEffectivePayments = true;
                    break;
                }
            }
            if (hasEffectivePayments) {
                throw new IllegalStateException(
                    "Esta factura tiene pagos aplicados. Para anularla, primero debe reversar "
                    + "el asiento de pago parcial correspondiente");
            }
            // Sin pagos efectivos: permitir anulacion. Restaurar status a
            // PENDING para coherencia antes de pasar a VOIDED.
            invoice.setStatus(StatusesInvoices.PENDING);
            // Si los JE de los pagos estan REVERSED, restaurar tambien
            // balanceDue/totalPayment para que la factura quede consistente.
            if (invoice.getTotalPayment() != null) {
                invoice.setBalanceDue(invoice.getTotalPayment());
            }
            log.info("HU-AP-25 E4: factura {} estaba PARTIALLY_PAID con pagos REVERSED; "
                    + "se restaura a PENDING + balanceDue=totalPayment para anulacion", id);
        }

        // E5: periodo cerrado bloqueado (mensaje literal HU)
        if (invoice.getInvoiceDate() != null) {
            try {
                accountingPeriodService.validatePeriodOpen(invoice.getInvoiceDate());
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                    "El período contable de esta factura está cerrado. "
                    + "La anulación debe realizarse mediante un ajuste en un período vigente");
            }
        }

        // E9: si tiene JE vinculado, decidir DRAFT->delete vs POSTED->reverse.
        // Chequeo estado ANTES de llamar para evitar excepciones que marquen la
        // TX como rollback-only y aborten la anulacion misma.
        Long journalEntryId = invoice.getJournalEntryId();
        if (journalEntryId != null) {
            try {
                JournalEntry je = journalEntryRepository.findById(journalEntryId).orElse(null);
                if (je != null) {
                    if (je.getStatus() == JournalEntryStatus.DRAFT) {
                        journalEntryService.deleteEntry(journalEntryId);
                        log.info("HU-AP-25: JE {} eliminado (DRAFT) por anulacion factura {}", journalEntryId, id);
                    } else if (je.getStatus() == JournalEntryStatus.POSTED) {
                        journalEntryService.reverseEntry(journalEntryId,
                            "Anulacion factura " + id + ": " + reason, "sistema");
                        log.info("HU-AP-25: JE {} reversado (POSTED) por anulacion factura {}", journalEntryId, id);
                    }
                    // si es REVERSED no hace falta tocar
                }
            } catch (Exception e) {
                log.warn("HU-AP-25: no se pudo procesar JE {} para anulacion factura {}: {}",
                        journalEntryId, id, e.getMessage());
            }
        }

        // Anulacion: cambia status, conserva motivo en notas
        StatusesInvoices previous = invoice.getStatus();
        invoice.setStatus(StatusesInvoices.VOIDED);
        String existingNotes = invoice.getNotes() != null ? invoice.getNotes() : "";
        invoice.setNotes((existingNotes.isBlank() ? "" : existingNotes + "\n")
                + "[ANULADA " + java.time.LocalDateTime.now() + "] " + reason);
        invoiceRepository.save(invoice);

        // E9 trazabilidad: audit + evento
        auditPublisher.publishUpdate(AuditModule.AP, "Invoice", id,
            "Factura " + invoice.getResolutionInvoice() + " ANULADA. Estado previo: "
            + previous + ". Motivo: " + reason);
        try {
            eventPublisher.publishEvent(new ApInvoiceUpdatedEvent(this, id,
                invoice.getTotalAmount() != null ? java.math.BigDecimal.valueOf(invoice.getTotalAmount()) : null,
                invoice.getThirdParty() != null ? invoice.getThirdParty().getId() : null));
        } catch (Exception e) {
            log.warn("No se pudo publicar evento ApInvoiceUpdatedEvent para anulacion {}: {}",
                    id, e.getMessage());
        }

        log.info("HU-AP-25: factura {} ANULADA. Estado previo: {}. Motivo: {}",
                id, previous, reason);

        // Payload minimo para evitar serializar proxies JPA (mismo patron de
        // createInvoiceAndReturnDto que documenta este issue de Jackson +
        // ByteBuddy). El cliente puede hacer GET /{id} si requiere DTO completo.
        java.util.Map<String, Object> minimal = new java.util.HashMap<>();
        minimal.put("id", invoice.getId());
        minimal.put("status", invoice.getStatus().name());
        minimal.put("previousStatus", previous.name());
        minimal.put("voidedReason", reason);
        return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
            Optional.of("Factura anulada exitosamente"), Optional.of(minimal)));
    }

    // ========================= Helpers

    /**
     * Resuelve el estado inicial de una factura segun su tipo y forma de pago.
     * Antes se hardcodeaba por id numerico (typeInvoiceId == 1 = FC), lo cual se
     * rompia segun el orden de ejecucion de las seeds. Ahora se consulta el
     * codigo del tipo (FC, FV, NC, ND) y se decide el estado inicial.
     */
    private InvoiceStates getInvoiceState(Long typeInvoiceId, Long paymentFormId) {
        TypesInvoices type = typeInvoiceRepository.findById(typeInvoiceId).orElseThrow(
            () -> new RuntimeException("El tipo de factura no existe")
        );
        String code = type.getCode();

        if ("FC".equalsIgnoreCase(code)) {
            // Factura de compra: id=1 si es contado (forma pago 1), id=2 en caso contrario
            Long stateId = (paymentFormId != null && paymentFormId == 1L) ? 1L : 2L;
            return invoiceStateRepository.findById(stateId).orElseThrow(
                () -> new RuntimeException("El estado de la factura no existe")
            );
        }
        throw new RuntimeException("Tipo de factura no soportado en este flujo: " + code);
    }

    private InvoiceDTO toDto(Invoices invoice) {

        List<LineInvoiceDTO> lineInvoices = new ArrayList<LineInvoiceDTO>();

        List<LinesInvoice> linesInvoice = linesInvoiceRepository.findAllByInvoiceId(invoice.getId());

        for(LinesInvoice lineInvoice : linesInvoice) {
            lineInvoices.add(toLineInvoiceDto(lineInvoice));
        }

        return InvoiceDTO.builder()
            .id(invoice.getId())
            .typeInvoice(invoice.getTypeInvoice())
            .invoiceState(invoice.getInvoiceState())
            .paymentForms(invoice.getPaymentForms())
            .user(invoice.getUser())
            .thirdParty(invoice.getThirdParty())
            .invoiceReference(invoice.getInvoiceReference())
            .resolution(invoice.getResolution())
            .invoiceDate(invoice.getInvoiceDate())
            .invoiceDueDay(invoice.getInvoiceDueDay())
            .totalPayment(invoice.getTotalPayment())
            .totalAmount(invoice.getTotalAmount())
            .totalDiscount(invoice.getTotalDiscount())
            .totalTax(invoice.getTotalTax())
            .status(invoice.getStatus())
            .supplierInvoiceNumber(invoice.getSupplierInvoiceNumber())
            .balanceDue(invoice.getBalanceDue())
            .journalEntryId(invoice.getJournalEntryId())
            .notes(invoice.getNotes())
            .lineInvoices(lineInvoices)
            .build();
    }

    private LineInvoiceDTO toLineInvoiceDto(LinesInvoice lineInvoice) {
        // Asset puede ser null cuando la linea se creo por cuenta contable generica
        ViewAssetsDTO assetDto = lineInvoice.getAsset() != null ? toViewAssetsDto(lineInvoice.getAsset()) : null;

        Long accountId = null;
        String accountCode = null;
        String accountName = null;
        if (lineInvoice.getAccountingAccount() != null) {
            AccountingAccount acc = lineInvoice.getAccountingAccount();
            accountId = acc.getId();
            accountName = acc.getCustomName();
            // El codigo viene del PUC asociado; se deja null si no se puede resolver para evitar lazy loading forzado
            try {
                if (acc.getPucAccount() != null) {
                    accountCode = acc.getPucAccount().getCode();
                }
            } catch (Exception ignored) {
                accountCode = null;
            }
        }

        return LineInvoiceDTO.builder()
            .id(lineInvoice.getId())
            .asset(assetDto)
            .accountingAccountId(accountId)
            .accountingAccountCode(accountCode)
            .accountingAccountName(accountName)
            .description(lineInvoice.getDescription())
            .quantity(lineInvoice.getQuantity())
            .unitPrice(lineInvoice.getPrice())
            .discount(lineInvoice.getDiscount())
            .tax(lineInvoice.getTax())
            .total(lineInvoice.getTotal())
            .build();
    }

    private ViewAssetsDTO toViewAssetsDto(Assets asset) {
        return ViewAssetsDTO.builder()
            .id(asset.getId())
            .assetCode(asset.getAssetCode())
            .name(asset.getAssetName())
            .description(asset.getDescription())
            .build();
    }

    // ========================= Importacion masiva

    /**
     * Importa facturas de compra de forma masiva desde un archivo CSV codificado en Base64.
     * Cada fila debe contener: thirdPartyId, paymentFormId, resolutionInvoice, invoiceDate, invoiceDueDay.
     * Las filas invalidas se registran como errores sin detener el proceso.
     *
     * @param fileBase64 contenido del archivo CSV en Base64
     * @param delimiter  separador de campos (por defecto coma)
     * @return ResponseEntity con el resumen de importacion
     */
    /**
     * HU-AP-22 (Bloque AU): NO se usa @Transactional aqui. Cada fila se inserta
     * en su propia TX via TransactionTemplate. Si una fila falla, su TX se
     * rollbackea pero las demas continuan. El metodo padre solo orquesta.
     */
    public ResponseEntity<?> bulkImportInvoices(String fileBase64, String delimiter) {
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(fileBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El contenido del archivo no es Base64 valido");
        }

        // HU-AP-22 (Bloque AS): detectar XLSX por magic bytes (PK\003\004 = ZIP signature)
        // y delegar al parser de Apache POI. Si no es XLSX, asumir CSV.
        boolean isXlsx = decoded.length >= 4
                && decoded[0] == 0x50 && decoded[1] == 0x4B
                && decoded[2] == 0x03 && decoded[3] == 0x04;

        List<String[]> rows;
        if (isXlsx) {
            rows = parseXlsxRows(decoded);
        } else {
            String content = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\\r?\\n");
            String sep = delimiter != null ? delimiter : ",";
            rows = new ArrayList<>();
            for (String line : lines) {
                rows.add(line.split(sep, -1));
            }
        }

        if (rows.size() < 2) {
            throw new IllegalArgumentException("El archivo debe contener al menos un encabezado y una fila de datos");
        }

        int totalRows = rows.size() - 1; // Excluir encabezado
        int[] successCount = { 0 };
        List<com.sigcon.backend.invoices.application.BulkImportResultDTO.RowError> errors = new ArrayList<>();

        // HU-AP-22 (Bloque AU): TransactionTemplate por fila. Cada fila tiene
        // su propio TX REQUIRES_NEW. Si una falla, su TX se rollbackea SOLO,
        // sin contaminar las demas.
        org.springframework.transaction.support.TransactionTemplate tt =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        tt.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Capturar el tenant actual ANTES del loop. Cada TX nueva debe
        // re-establecer el TenantContext porque corre en una sesion JPA
        // distinta (el filter de tenant lee del TenantContext en cada query).
        Long tenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
        boolean wasPlatform = com.sigcon.backend.platform.tenant.TenantContext.isPlatformAdmin();

        for (int i = 1; i < rows.size(); i++) {
            String[] fields = rows.get(i);
            if (fields == null || fields.length == 0) continue;
            boolean allEmpty = true;
            for (String f : fields) {
                if (f != null && !f.trim().isEmpty()) { allEmpty = false; break; }
            }
            if (allEmpty) continue;

            final int rowNum = i + 1;
            final String[] f = fields;
            try {
                tt.executeWithoutResult(status -> {
                    // Reasegurar tenant en la TX nueva
                    if (wasPlatform) com.sigcon.backend.platform.tenant.TenantContext.setPlatformAdmin(true);
                    if (tenantId != null) com.sigcon.backend.platform.tenant.TenantContext.setCompanyId(tenantId);

                    if (f.length < 5) {
                        throw new IllegalArgumentException("Numero insuficiente de campos (minimo 5)");
                    }
                    Long thirdPartyId = Long.parseLong(f[0].trim());
                    Long paymentFormId = Long.parseLong(f[1].trim());
                    String resolutionInvoice = f[2].trim();
                    java.time.LocalDate invoiceDate = java.time.LocalDate.parse(f[3].trim());
                    Integer invoiceDueDay = Integer.parseInt(f[4].trim());
                    String supplierInvoiceNumber = f.length > 5 ? f[5].trim() : null;
                    String notes = f.length > 6 ? f[6].trim() : null;

                    InvoiceFCRequestDTO request = InvoiceFCRequestDTO.builder()
                            .thirdPartyId(thirdPartyId)
                            .paymentFormId(paymentFormId)
                            .resolutionInvoice(resolutionInvoice)
                            .invoiceDate(invoiceDate)
                            .invoiceDueDay(invoiceDueDay)
                            .supplierInvoiceNumber(supplierInvoiceNumber)
                            .notes(notes)
                            .lineInvoices(new ArrayList<>())
                            .build();

                    Long fcTypeId = typeInvoiceRepository.findByCodeAndDeletedAtIsNull("FC")
                            .orElseThrow(() -> new RuntimeException(
                                    "Tipo de factura 'FC' no encontrado en types_invoices"))
                            .getId();
                    createInvoice(request, fcTypeId);
                    successCount[0]++;
                });
            } catch (Exception e) {
                String msg = e.getCause() != null && e.getCause().getMessage() != null
                        ? e.getCause().getMessage() : e.getMessage();
                if (msg == null) msg = e.toString();
                errors.add(com.sigcon.backend.invoices.application.BulkImportResultDTO.RowError.builder()
                        .row(rowNum).message(msg).build());
                log.warn("HU-AP-22 fila {} fallo: {}", rowNum, msg);
            }
        }

        com.sigcon.backend.invoices.application.BulkImportResultDTO result =
                com.sigcon.backend.invoices.application.BulkImportResultDTO.builder()
                        .totalRows(totalRows)
                        .successCount(successCount[0])
                        .errorCount(errors.size())
                        .errors(errors)
                        .build();

        log.info("Importacion masiva completada: {}/{} exitosas, {} errores",
                successCount[0], totalRows, errors.size());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Importacion masiva completada"), Optional.of(result)));
    }

    /**
     * HU-AP-22 (Bloque AS): parsea un archivo XLSX usando Apache POI.
     * Convierte cada fila en un String[] aplicando formateo numerico/fecha.
     */
    private List<String[]> parseXlsxRows(byte[] xlsxBytes) {
        List<String[]> result = new ArrayList<>();
        try (java.io.InputStream is = new java.io.ByteArrayInputStream(xlsxBytes);
             org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(is)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            org.apache.poi.ss.usermodel.DataFormatter formatter = new org.apache.poi.ss.usermodel.DataFormatter();
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
                if (row == null) {
                    result.add(new String[0]);
                    continue;
                }
                int last = row.getLastCellNum();
                String[] cells = new String[Math.max(0, last)];
                for (int c = 0; c < last; c++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(c);
                    String val;
                    if (cell == null) {
                        val = "";
                    } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                            val = cell.getLocalDateTimeCellValue().toLocalDate().toString();
                        } else {
                            double d = cell.getNumericCellValue();
                            if (d == Math.floor(d)) {
                                val = String.valueOf((long) d);
                            } else {
                                val = String.valueOf(d);
                            }
                        }
                    } else {
                        val = formatter.formatCellValue(cell);
                    }
                    cells[c] = val == null ? "" : val.trim();
                }
                result.add(cells);
            }
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo XLSX: " + e.getMessage());
        }
        return result;
    }

    // ========================= Helpers

    /**
     * Genera el asiento contable asociado a una factura de compra.
     * Debita la cuenta del gasto/activo (primera linea) y acredita la cuenta CxP del proveedor.
     * Si la generacion falla, se registra un warning pero no se revierte la factura.
     *
     * @param invoice    factura guardada con totales calculados
     * @param thirdParty tercero (proveedor) de la factura
     */
    /**
     * Resuelve la cuenta contable debito de una linea de factura.
     * Prioridad: cuenta contable directa (caso servicios/insumos) sobre
     * la cuenta contable del activo fijo asociado.
     *
     * @param line linea de factura
     * @return ID de la cuenta contable a debitar
     * @throws IllegalStateException si la linea no tiene cuenta contable asociada
     */
    private Long resolveDebitAccountId(LinesInvoice line) {
        if (line.getAccountingAccount() != null) {
            return line.getAccountingAccount().getId();
        }
        if (line.getAsset() != null && line.getAsset().getAccountingAccount() != null) {
            return line.getAsset().getAccountingAccount().getId();
        }
        throw new IllegalStateException("La linea no tiene cuenta contable asociada");
    }

    private void generateJournalEntry(Invoices invoice, ThirdParty thirdParty) {
        // IMPORTANTE: NO envolver en try/catch silencioso. journalEntryService.createEntry
        // es @Transactional y participa de la misma transaccion. Si falla (cuenta PUC
        // inactiva, periodo cerrado, mapeo no resuelto, partida doble desbalanceada, etc.)
        // Spring marca la tx como rollback-only. Tragarse la excepcion aqui y continuar
        // provocaria el error opaco "Transaction silently rolled back because it has
        // been marked as rollback-only" al final del flujo. Dejamos propagar el error
        // real al controller para que el usuario vea el motivo concreto del fallo.
        try {
            // Obtener cuenta contable de la primera linea (gasto/activo)
            List<LinesInvoice> lines = linesInvoiceRepository.findAllByInvoiceId(invoice.getId());
            if (lines.isEmpty()) {
                log.warn("No se generó asiento contable para factura {}: sin lineas de detalle", invoice.getId());
                return;
            }

            Long debitAccountId = resolveDebitAccountId(lines.get(0));

            // AP-24 E6: Asiento multilinea con cuentas PUC reales (resuelto en V31):
            //   Debito: cuenta gasto/activo por subtotal (desde la linea)
            //   Debito: IVA descontable (PUC 2408) por total_tax (si aplica)
            //   Credito: CxP proveedores (PUC 2205) por total_payment (neto)
            //   Credito: Retenciones practicadas (PUC 2365) por total_discount (si aplica)
            Long idIvaDescontable = accountMappingService.resolveOrThrow(
                    AccountingConcept.AP_IVA_DESCONTABLE);
            Long idCxpProveedores = accountMappingService.resolveOrThrow(
                    AccountingConcept.AP_PROVEEDORES);
            Long idRetPracticadas = accountMappingService.resolveOrThrow(
                    AccountingConcept.AP_RET_PRACTICADAS);

            // HU-AP-01 E1 FIX: el subtotal de la factura es la BASE GRAVABLE
            // (price*quantity por linea), NO `invoice.totalAmount` que aqui se
            // asigna como total NETO (subtotal + IVA - retenciones). Si
            // usaramos totalAmount como debito de gasto, la partida doble se
            // descuadraba en exactamente totalDiscount + (totalAmount-subtotal):
            //   D = totalAmount + totalTax
            //   C = totalPayment + totalDiscount
            // Solucion: calcular subtotal sumando price*quantity de cada linea.
            BigDecimal subtotal = lines.stream()
                .map(l -> BigDecimal.valueOf(
                    (l.getPrice() != null ? l.getPrice() : 0.0)
                  * (l.getQuantity() != null ? l.getQuantity() : 0.0)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalTax = BigDecimal.valueOf(invoice.getTotalTax() != null ? invoice.getTotalTax() : 0.0);
            BigDecimal totalDiscount = BigDecimal.valueOf(invoice.getTotalDiscount() != null ? invoice.getTotalDiscount() : 0.0);
            BigDecimal totalPayment = BigDecimal.valueOf(invoice.getTotalPayment() != null ? invoice.getTotalPayment() : 0.0);

            java.util.List<CreateJournalEntryLineRequest> jeLines = new java.util.ArrayList<>();

            // 1. Debito: cuenta gasto/activo por el subtotal (sin impuestos)
            jeLines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(debitAccountId)
                .debitAmount(subtotal)
                .creditAmount(BigDecimal.ZERO)
                .description("Compra " + invoice.getResolutionInvoice())
                .thirdPartyNit(thirdParty.getNit())
                .build());

            // 2. Debito: IVA descontable (PUC 2408) si aplica
            if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
                jeLines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(idIvaDescontable)
                    .debitAmount(totalTax)
                    .creditAmount(BigDecimal.ZERO)
                    .description("IVA descontable " + invoice.getResolutionInvoice())
                    .thirdPartyNit(thirdParty.getNit())
                    .build());
            }

            // 3. Credito: CxP proveedores (PUC 2205) por el total neto a pagar
            jeLines.add(CreateJournalEntryLineRequest.builder()
                .accountingAccountId(idCxpProveedores)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(totalPayment)
                .description("CxP " + (thirdParty.getBusinessName() != null ? thirdParty.getBusinessName() : thirdParty.getId()))
                .thirdPartyNit(thirdParty.getNit())
                .build());

            // 4. Credito: Retenciones en la fuente practicadas (PUC 2365) si aplica
            if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
                jeLines.add(CreateJournalEntryLineRequest.builder()
                    .accountingAccountId(idRetPracticadas)
                    .debitAmount(BigDecimal.ZERO)
                    .creditAmount(totalDiscount)
                    .description("Retenciones " + invoice.getResolutionInvoice())
                    .thirdPartyNit(thirdParty.getNit())
                    .build());
            }

            CreateJournalEntryRequest jeRequest = CreateJournalEntryRequest.builder()
                .entryDate(invoice.getInvoiceDate())
                .description("Factura compra " + invoice.getResolutionInvoice()
                    + " - " + (thirdParty.getBusinessName() != null ? thirdParty.getBusinessName() : thirdParty.getId()))
                .sourceModule(JournalSourceModule.AP)
                .sourceId(invoice.getId())
                .lines(jeLines)
                .build();

            JournalEntryDTO journalEntry = journalEntryService.createEntry(jeRequest, "sistema");
            invoice.setJournalEntryId(journalEntry.getId());
            invoiceRepository.save(invoice);
            log.info("Asiento contable {} generado para factura {} con {} lineas", journalEntry.getId(), invoice.getId(), jeLines.size());
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Errores de validacion de negocio (cuenta inactiva, periodo cerrado, mapeo
            // no resuelto, etc). Propagar con el mensaje original para que el usuario
            // vea el motivo concreto; la transaccion completa se rollbackea correctamente.
            log.error("Error generando asiento contable para factura {}: {}", invoice.getId(), e.getMessage());
            throw new IllegalStateException(
                    "No se pudo registrar la factura: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Error inesperado generando asiento para factura {}", invoice.getId(), e);
            throw e;
        }
    }

}
