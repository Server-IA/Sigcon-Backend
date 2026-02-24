package com.sigcon.backend.lists_accounting.accounting_lists.application;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "DTO para actualizar una cuenta contable del catalogo PUC")
public class UpdateChartOfAccountDTO {

    @NotBlank(message = "No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.")
    @Pattern(regexp = "^[0-9]{1,10}$", message = "Por favor siga el formato de los filtros")
    @Schema(description = "Codigo de la cuenta (solo numeros, maximo 10 digitos)", example = "110505")
    private String code;

    @NotBlank(message = "No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.")
    @Pattern(regexp = "^[A-Za-z0-9_\\-\\s]{1,100}$", message = "Por favor siga el formato de los filtros")
    @Schema(description = "Nombre de la cuenta", example = "Caja General")
    private String name;

    @NotNull(message = "No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.")
    @Schema(
            description = "Clase contable de la cuenta",
            example = "ASSET",
            allowableValues = {"ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE", "COST_OF_SALES", "PRODUCTION_COST", "MEMORANDUM_DEBIT", "MEMORANDUM_CREDIT"}
    )
    private AccountClass accountClass;

    @NotNull(message = "No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.")
    @Schema(description = "Nivel contable", example = "ACCOUNT", allowableValues = {"CLASS", "GROUP", "ACCOUNT", "SUBACCOUNT"})
    private AccountLevel level;

    @NotNull(message = "La naturaleza de la cuenta es invalida")
    @Schema(description = "Naturaleza contable", example = "DEBIT", allowableValues = {"DEBIT", "CREDIT"})
    private AccountNature nature;

    @NotNull(message = "No se puede actualizar la informacion. Por favor complete todos los campos obligatorios antes de continuar.")
    @Schema(description = "Estado de la cuenta", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
    private AccountStatus status;
}
