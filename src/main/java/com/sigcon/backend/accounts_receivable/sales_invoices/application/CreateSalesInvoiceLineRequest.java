package com.sigcon.backend.accounts_receivable.sales_invoices.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request para crear una linea de factura de venta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesInvoiceLineRequest {

    @Schema(description = "ID del item/producto", example = "1")
    private Long itemId;

    @Schema(description = "Descripcion del item", example = "Servicio de consultoria")
    private String description;

    @Schema(description = "Cantidad", example = "1.0")
    @NotNull(message = "La cantidad es requerida")
    private BigDecimal quantity;

    @Schema(description = "Precio unitario", example = "100000.00")
    @NotNull(message = "El precio unitario es requerido")
    private BigDecimal unitPrice;

    @Schema(description = "Descuento por linea", example = "0")
    @Builder.Default
    private BigDecimal discount = BigDecimal.ZERO;

    /** IDs de reglas tributarias aplicables (TAX y WITHHOLDING). */
    @Schema(description = "IDs de reglas tributarias a aplicar")
    @Builder.Default
    private List<Long> taxRuleIds = new ArrayList<>();

    /**
     * AAEF v1.1 (2026-04-28): override de cuenta contable PUC para esta linea.
     *
     * <p>Si viene null, el motor usa el mapeo PUC configurado en Parametrizacion
     * (comportamiento original). Si viene populado, sobreescribe la cuenta de
     * ingreso (debito en NC, credito en venta).
     *
     * <p>Solo se popula via mapper AAEF cuando el documento trae el campo
     * {@code accounting_account[0]}. Para creacion manual se deja null.
     */
    @Schema(description = "Override AAEF: ID accounting_account para debito de la linea")
    private Long accountDebitOverride;

    /**
     * AAEF v1.1 (2026-04-28): override de cuenta contable PUC para credito.
     * Solo se popula cuando el documento trae {@code accounting_account[1]}.
     */
    @Schema(description = "Override AAEF: ID accounting_account para credito de la linea")
    private Long accountCreditOverride;

    /**
     * AAEF QA Bloque BJ (HU-INT-RF-04 E1, 2026-05-18): override del impuesto
     * calculado por linea. Cuando AAEF envia {@code Taxes[]} con TaxType=VAT/IVA/ICA
     * y un Amount concreto, no podemos resolver una regla tributaria a nivel
     * de tenant (varia por empresa), pero SI tenemos el monto exacto. Este
     * override lo persiste directo en la linea sin pasar por SalesTaxEngine.
     * Si viene null, el motor calcula via taxRuleIds (comportamiento original).
     */
    @Schema(description = "Override AAEF: monto de IVA/impuesto generado para esta linea")
    private BigDecimal taxAmountOverride;

    /**
     * AAEF QA Bloque BJ (HU-INT-RF-04 E1, 2026-05-18): override de la retencion
     * practicada por linea (RTE_FTE, RTE_IVA, RTE_ICA en {@code Taxes[]}).
     * Si viene null, motor SalesTaxEngine via taxRuleIds.
     */
    @Schema(description = "Override AAEF: monto de retencion practicada para esta linea")
    private BigDecimal withholdingAmountOverride;
}
