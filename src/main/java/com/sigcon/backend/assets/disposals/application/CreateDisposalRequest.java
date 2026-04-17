package com.sigcon.backend.assets.disposals.application;

import com.sigcon.backend.assets.disposals.domain.model.enums.DisposalType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request para registrar una baja o transferencia de activo.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateDisposalRequest {

    /** Identificador del activo a dar de baja o transferir. */
    @NotNull(message = "El identificador del activo es obligatorio.")
    private Long assetId;

    /** Tipo de disposicion: BAJA o TRANSFERENCIA. */
    @NotNull(message = "El tipo de disposicion es obligatorio.")
    private DisposalType disposalType;

    /** Fecha de la operacion. */
    @NotNull(message = "La fecha de disposicion es obligatoria.")
    private LocalDate disposalDate;

    /** Monto de enajenacion (obligatorio para BAJA, ignorado en TRANSFERENCIA). */
    private BigDecimal disposalAmount;

    /** Motivo de la baja o transferencia. */
    @NotBlank(message = "Debe especificar el motivo de la operacion.")
    private String reason;

    /** Informacion del destino (solo para TRANSFERENCIA). */
    private String destinationInfo;
}
