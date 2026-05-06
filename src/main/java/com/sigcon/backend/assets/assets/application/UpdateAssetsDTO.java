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


        // HU-ACT-06 (QA 2026-05-05): edicion parcial. Cualquier campo no enviado
        // por el frontend se preserva con el valor actual del activo en el
        // service. Las validaciones de formato (@Pattern, @DecimalMin) siguen
        // aplicando solo cuando el campo llega no-null.
        @Pattern(regexp = "^[\\p{L}0-9\\-_/.,\\s]{3,150}$", message = "Faltan datos requeridos")
        @Schema(description = "Nombre del activo (opcional en update)", example = "Impresora laser oficina")
        private String name;

        @Size(max = 500, message = "Faltan datos requeridos")
        @Schema(description = "Descripcion funcional del activo", example = "Activo fijo para area administrativa")
        private String description;

        @Schema(description = "Clasificacion del activo (opcional en update)", example = "NON_CURRENT", allowableValues = { "CURRENT",
                        "NON_CURRENT" })
        private AssetClassification classification;

        @Schema(description = "Tipo del activo (opcional en update)", example = "TANGIBLE", allowableValues = { "TANGIBLE", "INTANGIBLE" })
        private AssetType type;

        @Positive(message = "Faltan datos requeridos")
        @Schema(description = "ID de la cuenta contable asociada al activo (opcional en update)", example = "12")
        private Long accountingAccountId;

        @Schema(description = "Estado del activo", example = "ACTIVE", allowableValues = { "ACTIVE", "IN_REPAIR", "DECOMMISSIONED", "TRANSFERRED" })
        private AssetStatus status;

        @DecimalMin(value = "0.01", message = "Faltan datos requeridos")
        @Schema(description = "Valor de adquisicion (opcional en update)", example = "3200000.00")
        private BigDecimal acquisitionValue;
        
        @Schema(description = "Regla tributaria para el calculo de impuestos", example = "1")
        private Long rulerTax;

        // Al editar un activo existente, la fecha de adquisicion ya esta registrada
        // en el voucher original. El form de edit no la expone, asi que la dejamos
        // OPCIONAL: si viene null, el servicio preserva el valor existente.
        @Schema(description = "Fecha de adquisicion (opcional en update)", example = "2026-01-15")
        private LocalDate acquisitionDate;

        @Min(value = 1, message = "Faltan datos requeridos")
        @Schema(description = "Vida util en meses (opcional en update)", example = "60")
        private Integer usefulLifeMonths;

        // Opcional en update: si el usuario no la cambia, se mantiene la regla actual.
        @Schema(description = "ID de la regla de depreciacion (opcional en update)", example = "1")
        private Long depreciationRuleId;

        // Opcional en update por la misma razon que paymentFormId/paymentMethodId.
        @Schema(description = "ID del proveedor (opcional en update)", example = "1")
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

        @Size(max = 500, message = "Las observaciones no pueden superar 500 caracteres.")
        @Schema(description = "Observaciones administrativas", example = "Pendiente de placa interna")
        private String observations;

        @Schema(description = "Impuestos o retenciones")
        private List<CreateAssetTaxesRetention> taxesRetention;

        @Schema(description = "Numero de factura (si se cambia a credito y aun no existe FC)", example = "FC-001-12345")
        private String resolutionInvoice;

        @Schema(description = "Dia de vencimiento factura credito (1-31)", example = "15")
        private Integer invoiceDueDay;
}
