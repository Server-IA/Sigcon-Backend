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

import com.sigcon.backend.assets.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetType;
import com.sigcon.backend.assets.assets.domain.model.enums.DepreciationMethod;

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
    @Schema(description = "Clasificacion del activo", example = "NON_CURRENT", allowableValues = {"CURRENT", "NON_CURRENT"})
    private AssetClassification classification;

    @NotNull(message = "Faltan datos requeridos")
    @Schema(description = "Tipo del activo", example = "TANGIBLE", allowableValues = {"TANGIBLE", "INTANGIBLE"})
    private AssetType type;

    @NotNull(message = "Faltan datos requeridos")
    @Positive(message = "Faltan datos requeridos")
    @Schema(description = "ID de la cuenta contable asociada al activo", example = "12")
    private Long accountingAccountId;

    @NotNull(message = "Faltan datos requeridos")
    @DecimalMin(value = "0.01", message = "Faltan datos requeridos")
    @Schema(description = "Valor de adquisicion", example = "3200000.00")
    private BigDecimal acquisitionValue;

    @NotNull(message = "Faltan datos requeridos")
    @Schema(description = "Fecha de adquisicion", example = "2026-01-15")
    private LocalDate acquisitionDate;

    @NotNull(message = "Faltan datos requeridos")
    @Min(value = 1, message = "Faltan datos requeridos")
    @Schema(description = "Vida util en meses", example = "60")
    private Integer usefulLifeMonths;

    @NotNull(message = "Faltan datos requeridos")
    @Schema(description = "Metodo de depreciacion", example = "STRAIGHT_LINE", allowableValues = {
            "STRAIGHT_LINE", "DECLINING_BALANCE", "UNITS_OF_PRODUCTION", "OTHER"
    })
    private DepreciationMethod depreciationMethod;

    @NotNull(message = "Faltan datos requeridos")
    @Positive(message = "Faltan datos requeridos")
    @Schema(description = "ID del proveedor (modulo Terceros)", example = "1")
    private Long supplierId;

    @NotBlank(message = "Faltan datos requeridos")
    @Size(max = 120, message = "Faltan datos requeridos")
    @Schema(description = "Condiciones de pago asociadas al activo", example = "30 dias")
    private String paymentTerms;

    @Schema(description = "Referencia del modulo de Cuentas por Pagar (pendiente de integrar)", example = "1001")
    private Long accountsPayableReferenceId;

    @Schema(description = "Referencia del modulo de Bancos/Cajas (pendiente de integrar)", example = "5001")
    private Long bankCashReferenceId;

    @NotNull(message = "Faltan datos requeridos")
    @Schema(description = "Estado del activo", example = "ACTIVE", allowableValues = {
            "ACTIVE", "IN_REPAIR", "DECOMMISSIONED", "TRANSFERRED"
    })
    private AssetStatus status;

    @Size(max = 500, message = "Faltan datos requeridos")
    @Schema(description = "Observaciones administrativas", example = "Pendiente de placa interna")
    private String observations;
}
