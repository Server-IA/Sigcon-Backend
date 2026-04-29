package com.sigcon.backend.lists_accounting.accounting_lists.application;

import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.lists_accounting.accounting_lists.domain.model.enums.AccountNature;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para crear una cuenta contable del catalogo PUC")
public class CreateChartOfAccountDTO {

    @NotBlank(message = "Por favor diligencie todos los campos obligatorios")
    @Pattern(
            regexp = "^(?:[0-9]{1}|[0-9]{2}|[0-9]{4}|[0-9]{6})$",
            message = "El codigo debe tener 1, 2, 4 o 6 digitos segun Catálogo de cuentas del PUC para comerciantes de Colombia."
    )
    @Schema(description = "Codigo de la cuenta (solo numeros de 1, 2, 4 o 6 digitos)", example = "110505")
    private String code;

    @NotBlank(message = "Por favor diligencie todos los campos obligatorios")
    // QA-BLOQUE-AO (2026-04-29): regex ampliado para aceptar parentesis, puntos
    // y comas. Estandar PUC Colombia (Decreto 2650/1993) usa formato "X (codigo)".
    @Pattern(regexp = "^[\\p{L}0-9 ()._,\\-]{1,100}$", message = "El nombre puede contener letras, numeros, espacios, guiones, parentesis, puntos y comas (max 100 chars)")
    @Schema(description = "Nombre de la cuenta", example = "Flota y equipo aéreo (1548)")
    private String name;

    @NotNull(message = "Por favor diligencie todos los campos obligatorios")
    @Schema(
            description = "Clase contable de la cuenta",
            example = "ASSET",
            allowableValues = {"ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE", "COST_OF_SALES", "PRODUCTION_COST", "MEMORANDUM_DEBIT", "MEMORANDUM_CREDIT"}
    )
    private AccountClass accountClass;

    @NotNull(message = "Por favor diligencie todos los campos obligatorios")
    @Schema(description = "Nivel contable", example = "ACCOUNT", allowableValues = {"CLASS", "GROUP", "ACCOUNT", "SUBACCOUNT"})
    private AccountLevel level;

    @NotNull(message = "Naturaleza de la cuenta no valida")
    @Schema(description = "Naturaleza contable", example = "DEBIT", allowableValues = {"DEBIT", "CREDIT"})
    private AccountNature nature;
}