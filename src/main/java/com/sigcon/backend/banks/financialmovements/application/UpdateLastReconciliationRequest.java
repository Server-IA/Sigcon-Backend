package com.sigcon.backend.banks.financialmovements.application;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLastReconciliationRequest {

    @NotNull(message = "La fecha de ultima conciliacion es obligatoria")
    private LocalDate lastReconciliationDate;
}
