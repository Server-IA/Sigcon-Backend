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
            () -> new IllegalArgumentException("Proveedor no válido o inactivo")
        );
        // HU-AP-01 E3: Proveedor inactivo o inexistente -> mensaje exacto del Excel
        if (thirdParty.getStatus() == null
                || !"ACTIVO".equalsIgnoreCase(thirdParty.getStatus().getName())) {
            throw new IllegalArgumentException("Proveedor no válido o inactivo");
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

        // No se puede modificar una factura anulada o liquidada
        if (invoice.getStatus() == StatusesInvoices.VOIDED || invoice.getStatus() == StatusesInvoices.SETTLED) {
            throw new IllegalStateException("No se puede modificar una factura anulada o liquidada.");
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

        // Actualizar campos editables
        if (request.getSupplierInvoiceNumber() != null) {
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
        if (request.getInvoiceDate() != null) {
            invoice.setInvoiceDate(request.getInvoiceDate());
        }
        if (request.getInvoiceDueDay() != null) {
            invoice.setInvoiceDueDay(request.getInvoiceDueDay());
        }
        if (request.getResolutionInvoice() != null) {
            invoice.setResolutionInvoice(request.getResolutionInvoice());
        }

        invoice = invoiceRepository.save(invoice);

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
                    } else {
                        // JE POSTED o REVERSED: no tocar.
                        jePostedSkipped = true;
                        log.warn("HU-AP-02 E1: JE {} en estado {} no se sincroniza tras update "
                                + "factura {}. Use correccion via comprobante de ajuste.",
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

        return ResponseEntity.ok(
            SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Factura liquidada exitosamente."), Optional.of(toDto(invoice))));
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
    @Transactional
    public ResponseEntity<?> bulkImportInvoices(String fileBase64, String delimiter) {
        byte[] decoded;
        try {
            decoded = java.util.Base64.getDecoder().decode(fileBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El contenido del archivo no es Base64 valido");
        }

        String content = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
        String[] rows = content.split("\\r?\\n");

        if (rows.length < 2) {
            throw new IllegalArgumentException("El archivo debe contener al menos un encabezado y una fila de datos");
        }

        String sep = delimiter != null ? delimiter : ",";
        int totalRows = rows.length - 1; // Excluir encabezado
        int successCount = 0;
        List<com.sigcon.backend.invoices.application.BulkImportResultDTO.RowError> errors = new ArrayList<>();

        for (int i = 1; i < rows.length; i++) {
            String row = rows[i].trim();
            if (row.isEmpty()) continue;

            try {
                String[] fields = row.split(sep);
                if (fields.length < 5) {
                    errors.add(com.sigcon.backend.invoices.application.BulkImportResultDTO.RowError.builder()
                            .row(i + 1).message("Numero insuficiente de campos (minimo 5)").build());
                    continue;
                }

                Long thirdPartyId = Long.parseLong(fields[0].trim());
                Long paymentFormId = Long.parseLong(fields[1].trim());
                String resolutionInvoice = fields[2].trim();
                java.time.LocalDate invoiceDate = java.time.LocalDate.parse(fields[3].trim());
                Integer invoiceDueDay = Integer.parseInt(fields[4].trim());
                String supplierInvoiceNumber = fields.length > 5 ? fields[5].trim() : null;
                String notes = fields.length > 6 ? fields[6].trim() : null;

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

                // Bulk import es siempre factura de compra (FC). Resolver por codigo para
                // ser robusto ante cambios en el orden de seed de types_invoices.
                Long fcTypeId = typeInvoiceRepository.findByCodeAndDeletedAtIsNull("FC")
                        .orElseThrow(() -> new RuntimeException(
                                "Tipo de factura 'FC' no encontrado en types_invoices"))
                        .getId();
                createInvoice(request, fcTypeId);
                successCount++;
            } catch (Exception e) {
                errors.add(com.sigcon.backend.invoices.application.BulkImportResultDTO.RowError.builder()
                        .row(i + 1).message(e.getMessage()).build());
            }
        }

        com.sigcon.backend.invoices.application.BulkImportResultDTO result =
                com.sigcon.backend.invoices.application.BulkImportResultDTO.builder()
                        .totalRows(totalRows)
                        .successCount(successCount)
                        .errorCount(errors.size())
                        .errors(errors)
                        .build();

        log.info("Importacion masiva completada: {}/{} exitosas, {} errores",
                successCount, totalRows, errors.size());

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Importacion masiva completada"), Optional.of(result)));
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
