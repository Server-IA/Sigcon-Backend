package com.sigcon.backend.integration.domain.service;

import com.sigcon.backend.accounts_receivable.advances.application.CreateArAdvanceRequest;
import com.sigcon.backend.accounts_receivable.payments.application.CreateArPaymentRequest;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.model.SalesInvoice;
import com.sigcon.backend.accounts_receivable.sales_invoices.domain.repository.SalesInvoiceRepository;
import com.sigcon.backend.integration.application.AaefTransactionDTO;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.repository.InvoiceRepository;
import com.sigcon.backend.third_parties.third_parties.domain.model.ThirdParty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/**
 * HU-INT-RF-05: Mapper de transacciones AAEF a DTOs internos de SIGCON.
 *
 * <p>Reglas de mapeo por {@code Type.Code}:
 * <ul>
 *   <li>{@code PAY} → {@code CreateArPaymentRequest} (si relatedInvoiceId es FV)
 *       o pago AP (si relatedInvoiceId es factura de compra).</li>
 *   <li>{@code ADV} → {@code CreateArAdvanceRequest} (sin RelatedInvoiceId).</li>
 *   <li>{@code REF}, {@code ADJ} → manejados en Fase 4 (Pull+Diff).</li>
 * </ul>
 *
 * <p>Validaciones:
 * <ul>
 *   <li>Status debe ser COMPLETED o REVERSED.</li>
 *   <li>RelatedInvoiceId es obligatorio si Type=PAY.</li>
 *   <li>AdjustmentReason es obligatorio si Type=ADJ.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AaefTransactionMapper {

    private static final Set<String> VALID_STATUSES = Set.of("COMPLETED", "REVERSED");

    public static final String TYPE_PAY = "PAY";
    public static final String TYPE_ADV = "ADV";
    public static final String TYPE_REF = "REF";
    public static final String TYPE_ADJ = "ADJ";

    /** Resultado de resolver una factura por external_id: indica si es AR o AP. */
    public enum InvoiceScope { AR, AP }

    private final ThirdPartyResolver thirdPartyResolver;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final InvoiceRepository invoiceRepository;

    // ---------- Tipo y validaciones comunes ----------

    /** Retorna el Type.Code en mayusculas, o lanza si la transaccion no es valida. */
    public String getTypeCode(AaefTransactionDTO tx) {
        if (tx == null || tx.getType() == null || tx.getType().getCode() == null) {
            throw new AaefMappingException(
                    AaefMappingException.MAPPING_ERROR,
                    "Transaction sin Type.Code");
        }
        return tx.getType().getCode().toUpperCase();
    }

    /**
     * Valida reglas comunes de una transaccion (status, campos obligatorios segun tipo).
     */
    public void validate(AaefTransactionDTO tx) {
        if (tx == null) {
            throw new AaefMappingException(AaefMappingException.MAPPING_ERROR,
                    "Transaction es null");
        }

        if (tx.getStatus() == null
                || !VALID_STATUSES.contains(tx.getStatus().toUpperCase())) {
            throw new AaefMappingException(
                    AaefMappingException.INVALID_STATUS,
                    "Status invalido: '" + tx.getStatus() + "'. Valores permitidos: "
                            + VALID_STATUSES);
        }

        if (tx.getAmount() == null) {
            throw new AaefMappingException(AaefMappingException.MAPPING_ERROR,
                    "Amount es obligatorio");
        }

        if (tx.getDate() == null) {
            throw new AaefMappingException(AaefMappingException.MAPPING_ERROR,
                    "Date es obligatorio");
        }

        String typeCode = getTypeCode(tx);

        if (TYPE_PAY.equals(typeCode)
                && (tx.getRelatedInvoiceId() == null || tx.getRelatedInvoiceId().trim().isEmpty())) {
            throw new AaefMappingException(
                    AaefMappingException.MISSING_INVOICE_REF,
                    "Type=PAY requiere RelatedInvoiceId");
        }

        if (TYPE_ADJ.equals(typeCode)
                && (tx.getAdjustmentReason() == null || tx.getAdjustmentReason().trim().isEmpty())) {
            throw new AaefMappingException(
                    AaefMappingException.MISSING_ADJUSTMENT_REASON,
                    "Type=ADJ requiere AdjustmentReason");
        }

        if (tx.getThirdParty() == null || tx.getThirdParty().getNit() == null) {
            throw new AaefMappingException(
                    AaefMappingException.UNKNOWN_THIRD_PARTY,
                    "ThirdParty.NIT es obligatorio");
        }
    }

    // ---------- Resolucion de factura referenciada ----------

    /**
     * Resuelve una factura (SalesInvoice o Invoices) por el externalId del AAEF.
     *
     * @return Optional con (scope, id) si se encontro, vacio si no existe.
     * @throws AaefMappingException si la factura no existe en ningun lado
     */
    public ResolvedInvoice resolveInvoiceByExternalId(String externalId) {
        if (externalId == null || externalId.trim().isEmpty()) {
            throw new AaefMappingException(
                    AaefMappingException.MISSING_INVOICE_REF,
                    "externalId vacio al resolver factura");
        }

        Optional<SalesInvoice> fv = salesInvoiceRepository
                .findByIntegrationSource_ExternalIdAndDeletedAtIsNull(externalId);
        if (fv.isPresent()) {
            return new ResolvedInvoice(InvoiceScope.AR, fv.get().getId());
        }

        Optional<Invoices> ap = invoiceRepository
                .findByIntegrationSource_ExternalIdAndDeletedAtIsNull(externalId);
        if (ap.isPresent()) {
            return new ResolvedInvoice(InvoiceScope.AP, ap.get().getId());
        }

        throw new AaefMappingException(
                AaefMappingException.ORIGINAL_NOT_FOUND,
                "No existe factura con externalId='" + externalId
                        + "' en SIGCON. Verifique que la factura haya sido recibida previamente.");
    }

    // ---------- Mapeo a request interno ----------

    /**
     * Mapea una transaccion PAY (cobro a FV) a CreateArPaymentRequest.
     */
    public CreateArPaymentRequest toArPaymentRequest(AaefTransactionDTO tx, Long invoiceId) {
        String paymentMethod = tx.getPaymentMethod() != null
                ? tx.getPaymentMethod().getCode()
                : "TRANSFER";
        return CreateArPaymentRequest.builder()
                .invoiceId(invoiceId)
                .amount(tx.getAmount())
                .paymentDate(tx.getDate())
                .paymentReference(tx.getDocumentId())
                .paymentMethod(paymentMethod)
                .notes("Importado desde AAEF - " + (tx.getNotes() != null ? tx.getNotes() : ""))
                .build();
    }

    /**
     * HU-AP-04 E5 + HU-INT-RF-05: Mapea una transaccion PAY a CreateApPaymentRequest
     * cuando la factura referenciada esta en el modulo AP (Cuentas por Pagar).
     */
    public com.sigcon.backend.invoices.ap_payments.application.CreateApPaymentRequest
            toApPaymentRequest(AaefTransactionDTO tx, Long invoiceId) {
        String paymentMethod = tx.getPaymentMethod() != null
                ? tx.getPaymentMethod().getCode()
                : "TRANSFER";
        return com.sigcon.backend.invoices.ap_payments.application.CreateApPaymentRequest.builder()
                .invoiceId(invoiceId)
                .amount(tx.getAmount())
                .paymentDate(tx.getDate())
                .paymentReference(tx.getDocumentId())
                .paymentMethod(paymentMethod)
                .notes("Importado desde AAEF - " + (tx.getNotes() != null ? tx.getNotes() : ""))
                .build();
    }

    /**
     * Mapea una transaccion ADV (anticipo de cliente) a CreateArAdvanceRequest.
     */
    public CreateArAdvanceRequest toArAdvanceRequest(AaefTransactionDTO tx) {
        ThirdParty resolved = thirdPartyResolver.findOrCreate(
                tx.getThirdParty().getNit(),
                null,
                tx.getThirdParty().getName());

        return CreateArAdvanceRequest.builder()
                .thirdPartyId(resolved.getId())
                .amount(tx.getAmount())
                .advanceDate(tx.getDate())
                .advanceReference(tx.getDocumentId())
                .notes("Importado desde AAEF - " + (tx.getNotes() != null ? tx.getNotes() : ""))
                .build();
    }

    /** Helper inmutable con resultado de resolver una factura externa. */
    public static class ResolvedInvoice {
        private final InvoiceScope scope;
        private final Long id;

        public ResolvedInvoice(InvoiceScope scope, Long id) {
            this.scope = scope;
            this.id = id;
        }

        public InvoiceScope getScope() { return scope; }
        public Long getId() { return id; }
    }
}
