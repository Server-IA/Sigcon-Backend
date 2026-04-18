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

    private static final Set<String> VALID_STATUSES =
            Set.of("ACTIVE", "PAID", "CANCELLED", "PARTIAL");

    private static final String SALES_TYPE_CODE = "01";
    private static final String PURCHASE_TYPE_CODE = "02";

    private final ThirdPartyResolver thirdPartyResolver;
    private final AccountMappingService accountMappingService;
    private final PaymentFormRepository paymentFormRepository;

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

        if (expected.compareTo(actual) != 0) {
            throw new AaefMappingException(
                    AaefMappingException.AMOUNT_MISMATCH,
                    "TotalPayment (" + actual + ") no coincide con Subtotal+TotalVAT"
                            + "-Retenciones-Descuentos (" + expected + ")");
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
                lines.add(CreateSalesInvoiceLineRequest.builder()
                        .description(
                                l.getDescription() != null
                                        ? l.getDescription()
                                        : (l.getName() != null ? l.getName() : l.getCode()))
                        .quantity(safe(l.getQuantity()))
                        .unitPrice(safe(l.getUnitPrice()))
                        .discount(BigDecimal.ZERO)
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

        if (expected.compareTo(actual) != 0) {
            throw new AaefMappingException(
                    AaefMappingException.AMOUNT_MISMATCH,
                    "TotalPayment (" + actual + ") no coincide con Subtotal+TotalVAT"
                            + "-Retenciones-Descuentos (" + expected + ")");
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
                lines.add(LineInvoiceRequestDTO.builder()
                        .accountingAccountId(defaultDebitAccountId)
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

    // ---------- Validaciones comunes ----------

    private void validateCommon(AaefInvoiceDTO invoice) {
        if (invoice == null || invoice.getHeader() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "La factura AAEF no tiene header");
        }
        AaefInvoiceDTO.Header h = invoice.getHeader();

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
