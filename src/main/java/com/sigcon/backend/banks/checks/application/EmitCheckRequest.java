package com.sigcon.backend.banks.checks.application;

import com.sigcon.backend.banks.checks.domain.model.enums.CheckType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EmitCheckRequest {

    @NotNull(message = "La chequera es obligatoria")
    @Schema(example = "1", description = "Id de la chequera")
    private Long checkbookId;

    @NotNull(message = "El numero de cheque es obligatorio")
    @Schema(example = "1001")
    private Integer numberCheck;

    @NotBlank(message = "El beneficiario es obligatorio")
    @Size(max = 200, message = "El beneficiario no puede superar 200 caracteres")
    @Schema(example = "PROVEEDOR ABC SAS")
    private String beneficiary;

    @NotNull(message = "El valor del cheque es obligatorio")
    @DecimalMin(value = "0.01", message = "El valor debe ser mayor a cero")
    @Schema(example = "1500000.00")
    private BigDecimal value;

    @NotBlank(message = "El concepto es obligatorio")
    @Size(max = 200, message = "El concepto no puede superar 200 caracteres")
    @Schema(example = "Pago de factura marzo")
    private String concept;

    @NotNull(message = "La fecha de expedicion es obligatoria")
    @Schema(example = "2026-03-18")
    private LocalDate issueDate;

    @Schema(example = "FISICO")
    private CheckType typeCheck;

    @Schema(example = "Observacion opcional")
    private String observations;

    @Schema(description = "Archivo soporte en base64 (obligatorio para cheque virtual)")
    private String supportDocumentBase64;

    @Schema(description = "Nombre original del archivo soporte", example = "soporte_cheque.pdf")
    private String supportDocumentFileName;
}
