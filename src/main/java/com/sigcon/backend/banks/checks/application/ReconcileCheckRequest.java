package com.sigcon.backend.banks.checks.application;

import com.sigcon.backend.banks.checks.domain.model.enums.ConciliationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReconcileCheckRequest {

    @NotNull(message = "La fecha de cobro es obligatoria")
    private LocalDate collectionDate;

    private Long financialMovementId;

    @NotNull(message = "El metodo de conciliacion es obligatorio")
    private ConciliationMethod conciliationMethod;

    @NotBlank(message = "La referencia de cobro es obligatoria")
    @Size(max = 100, message = "La referencia de cobro no puede superar 100 caracteres")
    private String collectionReference;
}
