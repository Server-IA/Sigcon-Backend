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

@Data
@Schema(description = "DTO para registrar un activo")
public class CreateAssetsDTO {



        @NotBlank(message = "El nombre del activo es obligatorio.")
        @Pattern(regexp = "^[\\p{L}0-9\\-_/.,\\s]{3,150}$", message = "El nombre solo admite letras, numeros y los caracteres - _ / . , (3 a 150 caracteres).")
        @Schema(description = "Nombre del activo", example = "Impresora laser oficina")
        private String name;

        @Size(max = 500, message = "La descripcion no puede superar 500 caracteres.")
        @Schema(description = "Descripcion funcional del activo", example = "Activo fijo para area administrativa")
        private String description;

        @NotNull(message = "La clasificacion es obligatoria (CURRENT o NON_CURRENT).")
        @Schema(description = "Clasificacion del activo", example = "NON_CURRENT", allowableValues = { "CURRENT",
                        "NON_CURRENT" })
        private AssetClassification classification;

        @NotNull(message = "El tipo de activo es obligatorio (TANGIBLE o INTANGIBLE).")
        @Schema(description = "Tipo del activo", example = "TANGIBLE", allowableValues = { "TANGIBLE", "INTANGIBLE" })
        private AssetType type;

        @NotNull(message = "La cuenta contable es obligatoria.")
        @Positive(message = "La cuenta contable es invalida.")
        @Schema(description = "ID de la cuenta contable asociada al activo", example = "12")
        private Long accountingAccountId;

        @NotNull(message = "El valor de adquisicion es obligatorio.")
        @DecimalMin(value = "0.01", message = "El valor de adquisicion debe ser mayor que cero.")
        @Schema(description = "Valor de adquisicion", example = "3200000.00")
        private BigDecimal acquisitionValue;

        @Schema(description = "Regla tributaria para el calculo de impuestos", example = "1")
        private Long rulerTax;

        @NotNull(message = "La fecha de adquisicion es obligatoria.")
        @Schema(description = "Fecha de adquisicion", example = "2026-01-15")
        private LocalDate acquisitionDate;

        @NotNull(message = "La vida util es obligatoria (en meses).")
        @Min(value = 1, message = "La vida util debe ser de al menos 1 mes.")
        @Schema(description = "Vida util en meses", example = "60")
        private Integer usefulLifeMonths;

        @NotNull(message = "La regla de depreciacion es obligatoria.")
        @Schema(description = "ID de la regla de depreciacion", example = "1")
        private Long depreciationRuleId;

        @NotNull(message = "El proveedor es obligatorio.")
        @Positive(message = "El proveedor es invalido.")
        @Schema(description = "ID del proveedor (modulo Terceros)", example = "1")
        private Long supplierId;

        @NotNull(message = "La forma de pago es requerida")
        @Schema(description = "ID de forma de pago", example = "1")
        private Long paymentFormId;

        // QA-2026-05-05: paymentMethodId es opcional aqui. Solo es obligatorio
        // cuando paymentFormId=1 (CONTADO). Para CREDITO no aplica metodo de
        // pago. La validacion condicional vive en AssetsService.create.
        @Schema(description = "ID del metodo de pago (obligatorio solo en CONTADO)", example = "1")
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

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres.")
        @Schema(description = "Observaciones administrativas", example = "Pendiente de placa interna")
        private String observations;

        @Schema(description = "Numero/resolucion de factura del proveedor (CREDITO crea factura AP)", example = "FC-001-12345")
        private String resolutionInvoice;

        @Schema(description = "Dia de vencimiento de la factura de credito (1-31)", example = "15")
        private Integer invoiceDueDay;

        @Schema(description = "Impuestos o retenciones")
        private List<CreateAssetTaxesRetention> taxesRetention;
}
