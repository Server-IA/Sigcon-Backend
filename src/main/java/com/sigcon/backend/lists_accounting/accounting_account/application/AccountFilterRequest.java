package com.sigcon.backend.lists_accounting.accounting_account.application;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Filtros y parámetros de búsqueda para consultar cuentas contables del catálogo PUC")
public class AccountFilterRequest {

    @Schema(
        description = "Filtro por nombre personalizado de la cuenta (búsqueda parcial, case-insensitive)", 
        example = "Caja Principal", 
        maxLength = 100
    )
    @Size(max = 100, message = "El filtro no puede superar los 100 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9 _-]*$", message = "Por favor siga el formato de los filtros")
    private String custom_name;

    @Schema(
        description = "Filtro por moneda base de la cuenta (código ISO 4217)", 
        example = "COP", 
        nullable = true
    )
    private String base_currency;

    @Schema(
        description = "Filtro por ID del centro de costos asociado a la cuenta", 
        example = "1", 
        nullable = true
    )
    private Long cost_center_id;

    @Schema(
        description = "Filtro por ID de regla de depreciación aplicada a la cuenta", 
        example = "1", 
        nullable = true
    )
    private Long depreciation_rule_id;

    @Schema(
        description = "Filtro por naturaleza contable de la cuenta", 
        example = "DEBIT", 
        allowableValues = {"DEBIT", "CREDIT"}, 
        nullable = true
    )
    private AccountNature nature;

    @Schema(
        description = "Filtro por estado de la cuenta en el sistema", 
        example = "ACTIVE", 
        allowableValues = {"ACTIVE", "INACTIVE"}, 
        nullable = true
    )
    private AccountStatus status;

    @Schema(
        description = "Filtro por ID del Plan Único de Cuentas (PUC) padre o categoría", 
        example = "1", 
        nullable = true
    )
    private Long puc_id;
}
