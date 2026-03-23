package com.sigcon.backend.banks.bankaccounts.application;

import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Request para cambiar estado de cuenta bancaria")
public class BankAccountChangeStatusRequest {

    @NotNull(message = "El estado destino es obligatorio")
    @Schema(description = "Estado destino", required = true)
    private BankAccountStatus status;

    @Schema(description = "Motivo del cambio (requerido para INACTIVA, SUSPENDIDA, CERRADA)")
    private String motivo;

    @Schema(description = "Fecha de cierre (obligatorio solo para estado CERRADA)")
    private LocalDate closingDate;
}
