package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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

    @JsonProperty("Date")
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

    /** COMPLETED | REVERSED (estados aceptados en AAEF). */
    @JsonProperty("Status")
    private String status;

    @JsonProperty("PaymentMethod")
    private PaymentMethod paymentMethod;

    /** Obligatorio si Type=ADJ. Justifica el ajuste. */
    @JsonProperty("AdjustmentReason")
    private String adjustmentReason;

    @JsonProperty("Notes")
    private String notes;

    @JsonProperty("UpdatedAt")
    private OffsetDateTime updatedAt;

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
