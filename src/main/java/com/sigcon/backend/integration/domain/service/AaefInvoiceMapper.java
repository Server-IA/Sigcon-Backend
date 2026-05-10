package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceLineRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.application.CreateSalesInvoiceRequest;
import com.sigcon.backend.integration.application.AaefInvoiceDTO;
import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.application.LineInvoiceRequestDTO;
import com.sigcon.backend.invoices.domain.model.PaymentForms;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountingConcept;
import com.sigcon.backend.parametrization.resources.domain.repository.PaymentFormRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * HU-INT-RF-04: Mapper de facturas AAEF a DTOs internos de SIGCON.
 *
 * <p>Reglas de mapeo:
 * <ul>
 *   <li>Type.Code=01 (Venta) → {@link CreateSalesInvoiceRequest}</li>
 *   <li>Type.Code=02 (Compra) → (en Paso posterior, con InvoiceService)</li>
 *   <li>Type.Code=03 (NC) / 04 (ND) → Pull+Diff (Fase 4)</li>
 * </ul>
 *
 * <p>Validaciones aplicadas:
 * <ul>
 *   <li>Status debe ser ACTIVE | PAID | CANCELLED | PARTIAL.</li>
 *   <li>TotalPayment debe coincidir con Subtotal + TotalVAT - Retenciones - Descuentos.</li>
 *   <li>Cada linea debe tener LineType (no vacio).</li>
 *   <li>Tercero se resuelve via {@link ThirdPartyResolver} (auto-crea si no existe).</li>
 * </ul>
 *
 * <p>NOTA: por ahora NO se mapean los taxRuleIds porque requiere resolver reglas
 * tributarias (CFG) por codigo o porcentaje. El motor interno calculara el JE con
 * las cuentas PUC reales via AccountMappingService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AaefInvoiceMapper {

    // QA Bloque PA Bug 70 (HU-INT-13, 2026-05-09): AgroFusion comunico que
    // los unicos status validos para facturas y nominas de Sigma/Disriego son
    // PAID y PENDING. Antes aceptabamos ACTIVE/CANCELLED/PARTIAL pero AgroFusion
    // no los emite — generaban INVALID_STATUS innecesarios. Mantenemos validacion
    // estricta para detectar payloads malformados temprano.
    private static final Set<String> VALID_STATUSES =
            Set.of("PAID", "PENDING");

    /** AgroFusion feedback v1.1 (2026-04-28): set completo de Type.Code validos. */
    public static final String SALES_TYPE_CODE = "01";          // Factura venta
    public static final String PURCHASE_TYPE_CODE = "02";       // Factura compra
    public static final String FEES_TYPE_CODE = "03";           // Honorarios (NUEVO)
    public static final String CREDIT_NOTE_TYPE_CODE = "04";    // Nota credito (movido de 03)
    public static final String DEBIT_NOTE_TYPE_CODE = "05";     // Nota debito (movido de 04)

    public static final Set<String> VALID_TYPE_CODES =
            Set.of(SALES_TYPE_CODE, PURCHASE_TYPE_CODE,
                   FEES_TYPE_CODE, CREDIT_NOTE_TYPE_CODE, DEBIT_NOTE_TYPE_CODE);

    private final ThirdPartyResolver thirdPartyResolver;
    private final AccountMappingService accountMappingService;
    private final PaymentFormRepository paymentFormRepository;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.sigcon.backend.lists_accounting.accounting_account.domain.repository.AccountingAccountRepository accountingAccountRepository;

    /**
     * Valida y mapea una factura AAEF de venta (Type=01) a CreateSalesInvoiceRequest.
     *
     * @throws AaefMappingException si la factura no cumple reglas de negocio
     */
    public CreateSalesInvoiceRequest toSalesInvoiceRequest(AaefInvoiceDTO invoice) {
        validateCommon(invoice);

        AaefInvoiceDTO.Header header = invoice.getHeader();
        AaefInvoiceDTO.ThirdParty tp = invoice.getThirdParty();
        AaefInvoiceDTO.Totals totals = invoice.getTotals();

        // Resolver o auto-crear tercero
        ThirdParty resolved = thirdPartyResolver.findOrCreate(
                tp.getNit(), tp.getDv(), tp.getName());

        // Validar cuadre de totales: TotalPayment = Subtotal + TotalVAT - Retenciones - Descuentos
        BigDecimal subtotal = safe(totals.getSubtotal());
        BigDecimal vat = safe(totals.getTotalVAT());
        BigDecimal withholdings = safe(totals.getTotalWithholdings());
        BigDecimal discounts = safe(totals.getTotalDiscounts());
        BigDecimal expected = subtotal.add(vat).subtract(withholdings).subtract(discounts);
        BigDecimal actual = safe(totals.getTotalPayment());

        // Spec RF-INT-12: tolerancia ±$0.01 por redondeos (la spec lo permite
        // explicitamente; antes haciamos compareTo() == 0 strict y rechazabamos
        // diferencias de centavos legitimas).
        if (expected.subtract(actual).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new AaefMappingException(
                    AaefMappingException.AMOUNT_MISMATCH,
                    "TotalPayment (" + actual + ") no coincide con Subtotal+TotalVAT"
                            + "-Retenciones-Descuentos (" + expected + "). Tolerancia ±$0.01.");
        }

        // Mapear lineas
        List<CreateSalesInvoiceLineRequest> lines = new ArrayList<>();
        if (invoice.getLines() != null) {
            for (AaefInvoiceDTO.Line l : invoice.getLines()) {
                if (l.getLineType() == null || l.getLineType().trim().isEmpty()) {
                    throw new AaefMappingException(
                            AaefMappingException.MISSING_LINE_TYPE,
                            "Linea '" + (l.getDescription() != null ? l.getDescription() : l.getCode())
                                    + "' no tiene LineType");
                }
                // AAEF v1.1: resolver override de accounting_account si viene en la linea
                Long[] accOverride = resolveAccountOverride(l);
                lines.add(CreateSalesInvoiceLineRequest.builder()
                        .description(
                                l.getDescription() != null
                                        ? l.getDescription()
                                        : (l.getName() != null ? l.getName() : l.getCode()))
                        .quantity(safe(l.getQuantity()))
                        .unitPrice(safe(l.getUnitPrice()))
                        .discount(BigDecimal.ZERO)
                        .accountDebitOverride(accOverride[0])
                        .accountCreditOverride(accOverride[1])
                        .build());
            }
        }

        if (lines.isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura debe tener al menos una linea");
        }

        // Construir request
        return CreateSalesInvoiceRequest.builder()
                .thirdPartyId(resolved.getId())
                .invoiceDate(header.getIssueDate())
                .dueDate(header.getDueDate() != null ? header.getDueDate() : header.getIssueDate())
                .resolutionNumber(resolveDocumentReference(header))
                .notes("Importado desde AAEF - DocumentId: " + header.getDocumentId())
                .lines(lines)
                .build();
    }

    /**
     * Arma el identificador externo de la factura para persistir en los campos
     * {@code supplier_invoice_number}/{@code resolution_invoice}. AgroFusion puede
     * enviar los datos de tres formas:
     * <ul>
     *   <li>{@code Prefix + Serial} completos (ej. {@code FCE + 1234} -> "FCE-1234")</li>
     *   <li>Solo uno de los dos (se usa el disponible)</li>
     *   <li>Ninguno (common - usar {@code DocumentId} como identificador principal)</li>
     * </ul>
     * Asi evitamos persistir literal {@code "null-null"} cuando el Header no trae
     * Prefix/Serial, como ocurria antes.
     */
    private String resolveDocumentReference(AaefInvoiceDTO.Header header) {
        String prefix = header.getPrefix();
        String serial = header.getSerial();
        boolean hasPrefix = prefix != null && !prefix.trim().isEmpty();
        boolean hasSerial = serial != null && !serial.trim().isEmpty();
        if (hasPrefix && hasSerial) return prefix + "-" + serial;
        if (hasPrefix)              return prefix;
        if (hasSerial)              return serial;
        return header.getDocumentId();
    }

    /**
     * HU-AP-01 E5 / HU-INT-RF-04 E2: Valida y mapea una factura AAEF de compra
     * (Type=02) a {@link InvoiceFCRequestDTO} usado por {@code InvoiceService}.
     *
     * <p>Particularidades respecto a ventas (Type=01):
     * <ul>
     *   <li>Cada linea usa como cuenta contable default {@code AP_COMPRAS_DEFAULT}
     *       (PUC 5135 Servicios). El contador puede reclasificar manualmente
     *       despues si corresponde.</li>
     *   <li>No se aplican reglas tributarias AAEF (taxRulesIds queda vacio):
     *       los totales IVA/retenciones ya vienen calculados desde AgroFusion
     *       y se materializan en el JE via {@code generateJournalEntry} del
     *       {@code InvoiceService}.</li>
     *   <li>La forma de pago se resuelve: 'CASH' si Status=PAID, credito en caso
     *       contrario (se toma la primera forma de pago a credito disponible).</li>
     * </ul>
     *
     * @return {@link InvoiceFCRequestDTO} listo para pasar a {@code InvoiceService.createInvoice}
     * @throws AaefMappingException si la factura no cumple reglas de negocio
     */
    public InvoiceFCRequestDTO toPurchaseInvoiceRequest(AaefInvoiceDTO invoice) {
        validateCommon(invoice);

        AaefInvoiceDTO.Header header = invoice.getHeader();
        AaefInvoiceDTO.ThirdParty tp = invoice.getThirdParty();
        AaefInvoiceDTO.Totals totals = invoice.getTotals();

        // Resolver o auto-crear tercero (HU-INT-RF-04 E6)
        ThirdParty resolved = thirdPartyResolver.findOrCreate(
                tp.getNit(), tp.getDv(), tp.getName());

        // Validar cuadre de totales: TotalPayment = Subtotal + TotalVAT - Retenciones - Descuentos
        BigDecimal subtotal = safe(totals.getSubtotal());
        BigDecimal vat = safe(totals.getTotalVAT());
        BigDecimal withholdings = safe(totals.getTotalWithholdings());
        BigDecimal discounts = safe(totals.getTotalDiscounts());
        BigDecimal expected = subtotal.add(vat).subtract(withholdings).subtract(discounts);
        BigDecimal actual = safe(totals.getTotalPayment());

        // Spec RF-INT-12: tolerancia ±$0.01 por redondeos (la spec lo permite
        // explicitamente; antes haciamos compareTo() == 0 strict y rechazabamos
        // diferencias de centavos legitimas).
        if (expected.subtract(actual).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new AaefMappingException(
                    AaefMappingException.AMOUNT_MISMATCH,
                    "TotalPayment (" + actual + ") no coincide con Subtotal+TotalVAT"
                            + "-Retenciones-Descuentos (" + expected + "). Tolerancia ±$0.01.");
        }

        // Resolver cuenta contable default (AP_COMPRAS_DEFAULT -> PUC 5135 Servicios)
        Long defaultDebitAccountId =
                accountMappingService.resolveOrThrow(AccountingConcept.AP_COMPRAS_DEFAULT);

        // Mapear lineas con cuenta contable default
        List<LineInvoiceRequestDTO> lines = new ArrayList<>();
        if (invoice.getLines() != null) {
            for (AaefInvoiceDTO.Line l : invoice.getLines()) {
                if (l.getLineType() == null || l.getLineType().trim().isEmpty()) {
                    throw new AaefMappingException(
                            AaefMappingException.MISSING_LINE_TYPE,
                            "Linea '" + (l.getDescription() != null ? l.getDescription() : l.getCode())
                                    + "' no tiene LineType");
                }
                BigDecimal qty = safe(l.getQuantity());
                BigDecimal unit = safe(l.getUnitPrice());
                // AAEF v1.1 (2026-04-28): override accounting_account[0] = cuenta debito.
                // Si la linea trae accounting_account, sobreescribe el default
                // AP_COMPRAS_DEFAULT (PUC 5135). El indice [1] (credito) NO se aplica
                // aqui porque el motor AP usa AP_PROVEEDORES (PUC 2205) por convencion
                // contable. Para casos especiales sera futuro work item.
                Long[] accOverride = resolveAccountOverride(l);
                Long debitAccount = (accOverride[0] != null) ? accOverride[0] : defaultDebitAccountId;
                lines.add(LineInvoiceRequestDTO.builder()
                        .accountingAccountId(debitAccount)
                        .description(
                                l.getDescription() != null
                                        ? l.getDescription()
                                        : (l.getName() != null ? l.getName() : l.getCode()))
                        .quantity(qty.doubleValue())
                        .price(unit.doubleValue())
                        .taxRulesIds(Collections.emptyList())
                        .build());
            }
        }

        if (lines.isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura debe tener al menos una linea");
        }

        // Forma de pago: si esta PAID -> CASH (isContado=true), sino primer credito disponible
        boolean isPaid = "PAID".equalsIgnoreCase(header.getStatus());
        Long paymentFormId = resolvePaymentFormId(isPaid);

        LocalDate issue = header.getIssueDate();
        LocalDate due = header.getDueDate() != null ? header.getDueDate() : issue;
        int dueDays = (int) Math.max(0, ChronoUnit.DAYS.between(issue, due));

        String docRef = resolveDocumentReference(header);
        return InvoiceFCRequestDTO.builder()
                .thirdPartyId(resolved.getId())
                .paymentFormId(paymentFormId)
                .invoiceDate(issue)
                .invoiceDueDay(dueDays)
                .resolutionInvoice(docRef)
                .supplierInvoiceNumber(docRef)
                .notes("Importado desde AAEF - DocumentId: " + header.getDocumentId())
                .lineInvoices(lines)
                .build();
    }

    /**
     * Resuelve la primera forma de pago cuyo flag {@code isContado} coincide con
     * el parametro. Si no encuentra, devuelve la primera disponible (fallback).
     */
    private Long resolvePaymentFormId(boolean wantCash) {
        return paymentFormRepository.findAll().stream()
                .filter(pf -> pf.getDeletedAt() == null)
                .filter(pf -> wantCash
                        ? Boolean.TRUE.equals(pf.getIsContado())
                        : !Boolean.TRUE.equals(pf.getIsContado()))
                .map(PaymentForms::getId)
                .findFirst()
                .orElseGet(() -> paymentFormRepository.findAll().stream()
                        .filter(pf -> pf.getDeletedAt() == null)
                        .map(PaymentForms::getId)
                        .findFirst()
                        .orElseThrow(() -> new AaefMappingException(
                                AaefMappingException.MAPPING_ERROR,
                                "No hay formas de pago activas en el sistema")));
    }

    /**
     * Determina si una factura AAEF es de venta (Type=01).
     */
    public boolean isSalesInvoice(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && SALES_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /**
     * Determina si una factura AAEF es de compra (Type=02).
     */
    public boolean isPurchaseInvoice(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && PURCHASE_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /**
     * AAEF v1.1 (2026-04-28): determina si una factura es de honorarios (Type=03).
     * Se procesa en AP con tratamiento tributario especial (retencion en la fuente
     * Art. 383/384 ET via motor existente).
     */
    public boolean isFeesInvoice(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && FEES_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /** AAEF v1.1: Type=04 Nota credito. */
    public boolean isCreditNote(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && CREDIT_NOTE_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /** AAEF v1.1: Type=05 Nota debito. */
    public boolean isDebitNote(AaefInvoiceDTO invoice) {
        return invoice.getHeader() != null
                && invoice.getHeader().getType() != null
                && DEBIT_NOTE_TYPE_CODE.equals(invoice.getHeader().getType().getCode());
    }

    /**
     * AAEF v1.1 (2026-04-28): valida que Type.Code este en la lista de valores
     * validos. Lanza {@link AaefMappingException#INVALID_TYPE_CODE} si no.
     *
     * @param invoice factura AAEF a validar
     */
    public void validateTypeCode(AaefInvoiceDTO invoice) {
        if (invoice == null || invoice.getHeader() == null
                || invoice.getHeader().getType() == null) {
            throw new AaefMappingException(AaefMappingException.INVALID_TYPE_CODE,
                    "Header.Type es obligatorio");
        }
        String code = invoice.getHeader().getType().getCode();
        if (code == null || !VALID_TYPE_CODES.contains(code)) {
            throw new AaefMappingException(AaefMappingException.INVALID_TYPE_CODE,
                    "Header.Type.Code='" + code + "' no es valido. Valores admitidos: "
                    + VALID_TYPE_CODES + " (01=Venta, 02=Compra, 03=Honorarios, 04=NC, 05=ND)");
        }
    }

    /**
     * AAEF v1.1 (2026-04-28): resuelve el override de cuentas PUC de una linea.
     *
     * <p>Si {@code accounting_account} viene presente:
     * <ul>
     *   <li>Valida que tenga maximo 2 elementos (lanza {@code INVALID_ACCOUNTING_ACCOUNT}).</li>
     *   <li>Valida que cada codigo PUC exista y este activo en el tenant
     *       (lanza {@code ACCOUNT_NOT_FOUND}).</li>
     * </ul>
     *
     * @param line linea AAEF (puede traer accounting_account null/vacio)
     * @return arreglo {@code [debitAccountId, creditAccountId]}; cualquiera puede
     *         ser {@code null} si no se override
     */
    public Long[] resolveAccountOverride(AaefInvoiceDTO.Line line) {
        Long[] result = new Long[]{null, null};
        if (line == null || line.getAccountingAccount() == null
                || line.getAccountingAccount().isEmpty()) {
            return result;
        }
        java.util.List<String> codes = line.getAccountingAccount();
        if (codes.size() > 2) {
            throw new AaefMappingException(
                    AaefMappingException.INVALID_ACCOUNTING_ACCOUNT,
                    "accounting_account no puede tener mas de 2 elementos. Recibido: "
                    + codes.size() + " (line code=" + line.getCode() + ")");
        }
        for (int i = 0; i < codes.size(); i++) {
            String pucCode = codes.get(i);
            if (pucCode == null || pucCode.isBlank()) {
                throw new AaefMappingException(
                        AaefMappingException.INVALID_ACCOUNTING_ACCOUNT,
                        "accounting_account[" + i + "] esta vacio (line code=" + line.getCode() + ")");
            }
            if (accountingAccountRepository == null) {
                // Si el repo no esta disponible (test unitario), retornamos nulls.
                continue;
            }
            Long tenantId = com.sigcon.backend.platform.tenant.TenantContext.getCompanyId();
            if (tenantId == null) {
                throw new AaefMappingException(
                        AaefMappingException.ACCOUNT_NOT_FOUND,
                        "TenantContext sin companyId al resolver accounting_account");
            }
            Long accId = accountingAccountRepository
                    .findActiveByPucCodeAndCompany(pucCode.trim(), tenantId)
                    .map(a -> a.getId())
                    .orElseThrow(() -> new AaefMappingException(
                            AaefMappingException.ACCOUNT_NOT_FOUND,
                            "Cuenta contable PUC '" + pucCode + "' no existe o esta "
                            + "inactiva en el sistema (line code=" + line.getCode() + ")"));
            result[i] = accId;
        }
        return result;
    }

    // ---------- Validaciones comunes ----------

    private void validateCommon(AaefInvoiceDTO invoice) {
        if (invoice == null || invoice.getHeader() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura AAEF no tiene header");
        }
        AaefInvoiceDTO.Header h = invoice.getHeader();

        // AAEF v1.1 (2026-04-28): validar Type.Code antes que el resto.
        validateTypeCode(invoice);

        if (h.getStatus() == null || !VALID_STATUSES.contains(h.getStatus().toUpperCase())) {
            throw new AaefMappingException(
                    AaefMappingException.INVALID_STATUS,
                    "Status invalido: '" + h.getStatus() + "'. Valores permitidos: "
                            + VALID_STATUSES);
        }

        if (h.getIssueDate() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "IssueDate es obligatoria");
        }

        if (invoice.getThirdParty() == null || invoice.getThirdParty().getNit() == null) {
            throw new AaefMappingException(
                    AaefMappingException.UNKNOWN_THIRD_PARTY,
                    "ThirdParty.NIT es obligatorio");
        }

        if (invoice.getTotals() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura debe tener Totals");
        }
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
