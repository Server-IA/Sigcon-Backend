package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AAEF v1.0 - DTO tipado para transacciones (RF-INT-13 seccion "transactions").
 *
 * <p>Cubre los 4 tipos de transaccion del catalogo AAEF:
 * <ul>
 *   <li>Type.Code = PAY → Pago de factura (→ ArPayment/ApPayment)</li>
 *   <li>Type.Code = ADV → Anticipo (→ ArAdvance/ApAdvance)</li>
 *   <li>Type.Code = REF → Reembolso (→ reversal JournalEntry)</li>
 *   <li>Type.Code = ADJ → Ajuste (→ correction JournalEntry)</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AaefTransactionDTO {

    @JsonProperty("DocumentId")
    private String documentId;

    @JsonProperty("Type")
    private Type type;

    /**
     * Fallo 1 (HU-AP-05 E4): tipo de documento inicial al que corresponde un
     * anticipo (Type=ADV), para distinguir el modulo destino:
     * <ul>
     *   <li>Code "01" (Venta)  → anticipo de CLIENTE   → AR / CxC (PUC 2805)</li>
     *   <li>Code "02" (Compra) → anticipo a PROVEEDOR  → AP / CxP (PUC 1330)</li>
     * </ul>
     * Si viene ausente, por compatibilidad el anticipo se trata como de cliente (AR).
     * Solo aplica a transacciones ADV; los demas tipos lo ignoran.
     */
    @JsonProperty("RelatedInvoiceType")
    private Type relatedInvoiceType;

    @JsonProperty("Date")
    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate date;

    /** Obligatorio si Type=PAY. Identifica la factura cruzada. */
    @JsonProperty("RelatedInvoiceId")
    private String relatedInvoiceId;

    @JsonProperty("ThirdParty")
    private ThirdPartyRef thirdParty;

    @JsonProperty("Amount")
    private BigDecimal amount;

    /** ISO 4217 */
    @JsonProperty("Currency")
    private String currency;

    /**
     * COMPLETED | REVERSED (estados validos de transaccion en AAEF v1.1).
     * COMPLETED = aplicada exitosamente; REVERSED = revertida o anulada.
     * (QA Integracion 2026-05-26: alineado al manual vigente.)
     */
    @JsonProperty("Status")
    private String status;

    @JsonProperty("PaymentMethod")
    private PaymentMethod paymentMethod;

    /** Obligatorio si Type=ADJ. Justifica el ajuste. */
    @JsonProperty("AdjustmentReason")
    private String adjustmentReason;

    @JsonProperty("Notes")
    private String notes;

    /** Fecha de ultima actualizacion. Tolerante a multiples formatos (Bloque AX). */
    @JsonProperty("UpdatedAt")
    @JsonDeserialize(using = FlexibleLocalDateDeserializer.class)
    private LocalDate updatedAt;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Type {
        /** PAY | ADV | REF | ADJ */
        @JsonProperty("Code") private String code;
        @JsonProperty("Name") private String name;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ThirdPartyRef {
        @JsonProperty("NIT") private String nit;
        @JsonProperty("Name") private String name;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentMethod {
        /** TRANSFER | CASH | CHECK | CARD | PSE */
        @JsonProperty("Code") private String code;
        @JsonProperty("Name") private String name;
    }
}
