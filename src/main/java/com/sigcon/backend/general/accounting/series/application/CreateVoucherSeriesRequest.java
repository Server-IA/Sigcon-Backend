package com.sigcon.backend.general.accounting.series.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request para crear o actualizar una VoucherSeriesConfig. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateVoucherSeriesRequest {

    @NotBlank(message = "El tipo de comprobante es obligatorio")
    @Size(min = 2, max = 20, message = "El tipo debe tener entre 2 y 20 caracteres")
    @Pattern(regexp = "^[A-Z0-9_]+$",
             message = "El tipo solo admite mayusculas, digitos y guion bajo (ej. JE, AJ, CI, REV)")
    private String voucherType;

    @NotBlank(message = "El prefijo es obligatorio")
    @Size(min = 2, max = 20)
    @Pattern(regexp = "^[A-Z0-9-]+$",
             message = "El prefijo solo admite mayusculas, digitos y guion (ej. JE, AJ-X, REV)")
    private String prefix;

    @NotNull(message = "El numero inicial es obligatorio")
    @Positive(message = "El numero inicial debe ser positivo")
    private Long startNumber;

    @NotNull(message = "El numero final es obligatorio")
    @Positive
    private Long endNumber;

    /** Numero actual (ultimo asignado). 0 = no se ha consumido ningun consecutivo aun. */
    @Min(0)
    private Long currentNumber;

    @NotNull
    @Min(value = 0, message = "El umbral de alerta debe estar entre 0 y 100")
    @Max(value = 100, message = "El umbral de alerta debe estar entre 0 y 100")
    private Integer alertThresholdPct;

    @Size(max = 255)
    private String description;
}
