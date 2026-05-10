package com.sigcon.backend.integration.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        /**
         * PAID | PENDING (estados aceptados en AAEF v1.1).
         *
         * <p>QA Bloque PA Bug 70 (HU-INT-13, 2026-05-09): AgroFusion solo
         * emite PAID o PENDING. Antes aceptabamos ACTIVE/CANCELLED/PARTIAL.
         */
        @JsonProperty("Status") private String status;
        /** Fecha de ultima actualizacion (yyyy-MM-dd, sin hora). */
        @JsonProperty("UpdatedAt") private LocalDate updatedAt;
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

        /**
         * Override de cuentas PUC para esta linea (AAEF feedback v1.1, 2026-04-28).
         *
         * <p>Array opcional con maximo 2 elementos:
         * <ul>
         *   <li>{@code accounting_account[0]} = cuenta DEBITO (siempre que array no sea vacio)</li>
         *   <li>{@code accounting_account[1]} = cuenta CREDITO (solo si array tiene 2 elementos)</li>
         * </ul>
         *
         * <p>Si viene null o vacio: el sistema usa el mapeo PUC configurado en
         * Parametrizacion (comportamiento original).
         *
         * <p>Validaciones:
         * <ul>
         *   <li>Maximo 2 elementos. Mas de 2 -> {@code INVALID_ACCOUNTING_ACCOUNT}</li>
         *   <li>Cada codigo debe existir y estar activo en el PUC del tenant. Si no
         *       existe -> {@code ACCOUNT_NOT_FOUND}</li>
         * </ul>
         */
        @JsonProperty("accounting_account")
        private List<String> accountingAccount;
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
