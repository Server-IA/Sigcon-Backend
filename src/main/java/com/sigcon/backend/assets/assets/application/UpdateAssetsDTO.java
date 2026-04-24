package com.sigcon.backend.assets.assets.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.sigcon.backend.assets.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetType;
import com.sigcon.backend.assets.assets_depreciation.domain.model.enums.DepreciationMethod;

@Data
@Schema(description = "DTO para actualizar un activo")
public class UpdateAssetsDTO {


        @NotBlank(message = "Faltan datos requeridos")
        @Pattern(regexp = "^[\\p{L}0-9\\-_/.,\\s]{3,150}$", message = "Faltan datos requeridos")
        @Schema(description = "Nombre del activo", example = "Impresora laser oficina")
        private String name;

        @Size(max = 500, message = "Faltan datos requeridos")
        @Schema(description = "Descripcion funcional del activo", example = "Activo fijo para area administrativa")
        private String description;

        @NotNull(message = "Faltan datos requeridos")
        @Schema(description = "Clasificacion del activo", example = "NON_CURRENT", allowableValues = { "CURRENT",
                        "NON_CURRENT" })
        private AssetClassification classification;

        @NotNull(message = "Faltan datos requeridos")
        @Schema(description = "Tipo del activo", example = "TANGIBLE", allowableValues = { "TANGIBLE", "INTANGIBLE" })
        private AssetType type;

        @NotNull(message = "Faltan datos requeridos")
        @Positive(message = "Faltan datos requeridos")
        @Schema(description = "ID de la cuenta contable asociada al activo", example = "12")
        private Long accountingAccountId;

        @Schema(description = "Estado del activo", example = "ACTIVE", allowableValues = { "ACTIVE", "IN_REPAIR", "DECOMMISSIONED", "TRANSFERRED" })
        private AssetStatus status;

        @NotNull(message = "Faltan datos requeridos")
        @DecimalMin(value = "0.01", message = "Faltan datos requeridos")
        @Schema(description = "Valor de adquisicion", example = "3200000.00")
        private BigDecimal acquisitionValue;
        
        @Schema(description = "Regla tributaria para el calculo de impuestos", example = "1")
        private Long rulerTax;

        @NotNull(message = "Faltan datos requeridos")
        @Schema(description = "Fecha de adquisicion", example = "2026-01-15")
        private LocalDate acquisitionDate;

        @NotNull(message = "Faltan datos requeridos")
        @Min(value = 1, message = "Faltan datos requeridos")
        @Schema(description = "Vida util en meses", example = "60")
        private Integer usefulLifeMonths;

        @NotNull(message = "Faltan datos requeridos")
        @Schema(description = "ID de la regla de depreciacion", example = "1")
        private Long depreciationRuleId;

        @NotNull(message = "Faltan datos requeridos")
        @Positive(message = "Faltan datos requeridos")
        @Schema(description = "ID del proveedor (modulo Terceros)", example = "1")
        private Long supplierId;

        // Al editar un activo existente, la forma/metodo de pago son OPCIONALES:
        // se persistieron en el voucher al momento de la creacion (HU-ACT-01 E8) y
        // no deberian exigirse cada vez que el usuario edite nombre/vida util/etc.
        // Si vienen null, el servicio preserva el valor existente.
        @Schema(description = "ID de forma de pago (opcional en update)", example = "1")
        private Long paymentFormId;

        @Schema(description = "ID del metodo de pago (opcional en update)", example = "1")
        private Long paymentMethodId;

        @Schema(description = "ID de la cuenta bancaria de origen", example = "1")
        private Long bankAccountId;

        @Schema(description = "ID de la cuenta de caja de origen", example = "1")
        private Long cashAccountId;

        @Schema(description = "ID del cheque de origen", example = "1")
        private Long checkId;

        @Schema(description = "Referencia del modulo de Cuentas por Pagar (pendiente de integrar)", example = "1001")
        private Long accountsPayableReferenceId;

        @Schema(description = "Referencia del modulo de Bancos/Cajas (pendiente de integrar)", example = "5001")
        private Long bankCashReferenceId;

        // @Schema(description = "Estado inicial del activo", example = "ACTIVE", allowableValues = {
        //                 "ACTIVE", "IN_REPAIR", "DECOMMISSIONED", "TRANSFERRED"
        // })
        // private AssetStatus status;

        @Size(max = 500, message = "Faltan datos requeridos")
        @Schema(description = "Observaciones administrativas", example = "Pendiente de placa interna")
        private String observations;

        @Schema(description = "Impuestos o retenciones")
        private List<CreateAssetTaxesRetention> taxesRetention;
}
