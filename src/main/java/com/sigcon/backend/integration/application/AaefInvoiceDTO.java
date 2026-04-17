package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AAEF v1.0 - DTO tipado para facturas (RF-INT-13 seccion "invoices").
 *
 * <p>Cubre los 4 tipos de factura del catalogo AAEF:
 * <ul>
 *   <li>Type.Code = 01 → Factura de Venta (→ SalesInvoice en SIGCON)</li>
 *   <li>Type.Code = 02 → Factura de Compra (→ Invoices AP en SIGCON)</li>
 *   <li>Type.Code = 03 → Nota Credito (→ ArCreditDebitNote/ApCreditDebitNote)</li>
 *   <li>Type.Code = 04 → Nota Debito (→ ArCreditDebitNote/ApCreditDebitNote)</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AaefInvoiceDTO {

    @JsonProperty("Header")
    private Header header;

    @JsonProperty("ThirdParty")
    private ThirdParty thirdParty;

    @JsonProperty("Totals")
    private Totals totals;

    @JsonProperty("Lines")
    private List<Line> lines;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Header {
        @JsonProperty("DocumentId") private String documentId;
        @JsonProperty("Prefix") private String prefix;
        @JsonProperty("Serial") private String serial;
        @JsonProperty("Type") private Type type;
        @JsonProperty("IssueDate") private LocalDate issueDate;
        @JsonProperty("DueDate") private LocalDate dueDate;
        /** ACTIVE | PAID | CANCELLED | PARTIAL (estados aceptados en AAEF). */
        @JsonProperty("Status") private String status;
        @JsonProperty("UpdatedAt") private OffsetDateTime updatedAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Type {
        /** 01 Venta | 02 Compra | 03 NC | 04 ND */
        @JsonProperty("Code") private String code;
        @JsonProperty("Name") private String name;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ThirdParty {
        @JsonProperty("Id") private String id;
        @JsonProperty("NIT") private String nit;
        @JsonProperty("DV") private String dv;
        @JsonProperty("Name") private String name;
        @JsonProperty("Address") private String address;
        @JsonProperty("City") private String city;
        /** ISO 3166-1 (ej: CO) */
        @JsonProperty("Country") private String country;
        @JsonProperty("Email") private String email;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Totals {
        @JsonProperty("Subtotal") private BigDecimal subtotal;
        @JsonProperty("TotalVAT") private BigDecimal totalVAT;
        @JsonProperty("TotalWithholdings") private BigDecimal totalWithholdings;
        @JsonProperty("TotalDiscounts") private BigDecimal totalDiscounts;
        @JsonProperty("TotalPayment") private BigDecimal totalPayment;
        @JsonProperty("OutstandingBalance") private BigDecimal outstandingBalance;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Line {
        @JsonProperty("Code") private String code;
        @JsonProperty("Name") private String name;
        @JsonProperty("Description") private String description;
        /** Naturaleza del item (abierto, sin catalogo cerrado en AAEF). */
        @JsonProperty("LineType") private String lineType;
        @JsonProperty("Quantity") private BigDecimal quantity;
        @JsonProperty("UnitPrice") private BigDecimal unitPrice;
        @JsonProperty("Value") private BigDecimal value;
        @JsonProperty("Taxes") private List<Tax> taxes;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Tax {
        /** IVA | ICA | RTE_IVA | RTE_FTE | RTE_ICA */
        @JsonProperty("TaxType") private String taxType;
        @JsonProperty("Rate") private BigDecimal rate;
        @JsonProperty("Amount") private BigDecimal amount;
    }
}
