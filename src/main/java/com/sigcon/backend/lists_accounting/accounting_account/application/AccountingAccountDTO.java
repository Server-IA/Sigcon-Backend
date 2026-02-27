package com.sigcon.backend.lists_accounting.accounting_account.application;

import java.time.LocalDateTime;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respuesta con los datos de una cuenta contable del catálogo para visualización en tablas y formularios")
public class AccountingAccountDTO {

    @Schema(description = "ID único interno de la cuenta contable", example = "1")
    private Long id;

    @Schema(description = "ID del Plan Único de Cuentas (PUC) asociado como categoría padre", example = "1", nullable = true)
    private Long puc_id;

    @Schema(description = "Código oficial de la cuenta según el estándar PUC", example = "1105", nullable = true)
    private String puc_code;

    @Schema(description = "Nombre personalizado de la cuenta para visualización en el sistema", example = "Caja General", nullable = true)
    private String custom_name;

    @Schema(description = "Moneda base de la cuenta (código ISO 4217)", example = "COP", nullable = true)
    private String base_currency;

    @Schema(description = "ID del centro de costos asociado a la cuenta", example = "1", nullable = true)
    private Long cost_center_id;

    @Schema(description = "Nombre del centro de costos para visualización en tablas", example = "Administración Central", nullable = true)
    private String cost_center_name;

    @Schema(description = "ID de la regla de depreciación aplicada a esta cuenta", example = "1", nullable = true)
    private Long depreciation_rule_id;

    @Schema(description = "Nombre de la regla de depreciación para visualización en tablas", example = "Depreciación Lineal 5 años", nullable = true)
    private String depreciation_rule_name;

    @Schema(
        description = "Naturaleza contable de la cuenta: define si aumenta por débito o crédito", 
        example = "DEBIT", 
        allowableValues = {"DEBIT", "CREDIT"}
    )
    private AccountNature nature;

    @Schema(
        description = "Estado actual de la cuenta en el sistema", 
        example = "ACTIVE", 
        allowableValues = {"ACTIVE", "INACTIVE"}
    )
    private AccountStatus status;

    @Schema(
        description = "Fecha y hora de creación del registro (UTC)", 
        example = "2024-02-21T10:30:00", 
        nullable = true
    )
    private LocalDateTime createdAt;
    
    @Schema(
        description = "Fecha y hora de última actualización del registro (UTC)", 
        example = "2024-02-21T10:30:00", 
        nullable = true
    )
    private LocalDateTime updatedAt;
    
    @Schema(
        description = "Fecha y hora de eliminación lógica del registro (null si la cuenta está activa)", 
        example = "2024-02-21T10:30:00", 
        nullable = true
    )
    private LocalDateTime deletedAt;
}
