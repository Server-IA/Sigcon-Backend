package com.sigcon.backend.lists_accounting.accounting_lists.application;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "DTO para filtros de busqueda de cuentas contables")
public class ViewChartOfAccountDTO {

    @Pattern(regexp = "^[0-9]{1,100}$", message = "Por favor siga el formato de los filtros")
    @Schema(description = "Filtro por codigo de cuenta", example = "1105")
    private String code;

    @Pattern(regexp = "^[\\p{L}0-9_\\-\\s]{1,100}$", message = "Por favor siga el formato de los filtros")
    @Schema(description = "Filtro por nombre de cuenta", example = "Caja")
    private String name;

    @Schema(
            description = "Filtro por clase contable",
            example = "ASSET",
            allowableValues = {"ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE", "COST_OF_SALES", "PRODUCTION_COST", "MEMORANDUM_DEBIT", "MEMORANDUM_CREDIT"}
    )
    private AccountClass accountClass;

    @Schema(description = "Filtro por nivel contable", example = "ACCOUNT", allowableValues = {"CLASS", "GROUP", "ACCOUNT", "SUBACCOUNT"})
    private AccountLevel level;

    @Schema(description = "Filtro por naturaleza contable", example = "DEBIT", allowableValues = {"DEBIT", "CREDIT"})
    private AccountNature nature;

    @Schema(description = "Filtro por estado de la cuenta", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
    private AccountStatus status;
}