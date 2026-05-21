package com.sigcon.backend.banks.bankaccounts.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request para actualizar cuenta bancaria")
public class UpdateBankAccountRequest {

    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(max = 100)
    @Schema(description = "Nombre descriptivo de la cuenta", required = true)
    private String accountName;

    @Size(max = 100)
    @Schema(description = "Sucursal del banco")
    private String branchName;

    @Size(max = 100)
    @Schema(description = "Nombre del ejecutivo de cuenta")
    private String accountExecutive;

    // @Size(max = 20)
    // @Pattern(regexp = "^[0-9]{7,20}$", message = "El teléfono debe tener entre 7 y 20 dígitos")
    // @Schema(description = "Teléfono de contacto del banco")
    // private String bankPhone;

    @Size(max = 500)
    @Schema(description = "Descripción adicional")
    private String description;

    @Schema(description = "Permite sobregiro")
    private Boolean allowsOverdraft;

    @Schema(description = "Límite de crédito (requerido si allowsOverdraft=true)")
    private BigDecimal creditLimit;

    @Schema(description = "Alertas de saldo bajo")
    private Boolean notifyLowBalance;

    @Schema(description = "Saldo mínimo para alertas (requerido si notifyLowBalance=true)")
    private BigDecimal minimumBalance;

    @Schema(description = "ID del centro de costo")
    private Long costCenterId;

    // BNK-HU-001 E5/E6 — editables tras creacion (HU-002 los lista como editables).
    @Schema(description = "BNK-HU-001 E5: la cuenta esta sujeta al GMF 4x1000")
    private Boolean aplicaGmf;

    @Schema(description = "BNK-HU-001 E5: accounting_accounts.id para GMF. Obligatorio si aplicaGmf=true")
    private Long cuentaGmfPucId;

    @Schema(description = "BNK-HU-001 E6: equivalente de efectivo segun NIC 7")
    private Boolean esEquivalenteEfectivo;

    @Size(min = 10, message = "El motivo de cambio debe tener al menos 10 caracteres")
    @Schema(description = "Motivo para cambios sensibles (requerido cuando aplica)")
    private String changeReason;
}
