package com.sigcon.backend.lists_accounting.accounting_account.application;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Datos requeridos para crear una nueva cuenta contable en el catálogo del sistema")
public class CreateAccountingAccountRequest {

    @Schema(
        description = "ID del Plan Único de Cuentas (PUC) que define la categoría padre de esta cuenta", 
        example = "1", 
        nullable = false
    )
    @NotNull(message = "El identificador PUC es obligatorio")
    private Long puc_id;

    @Schema(
        description = "Nombre personalizado de la cuenta para visualización en el sistema", 
        example = "Caja Principal Bogotá", 
        maxLength = 50
    )
    @NotBlank(message = "El nombre de la cuenta personalizada es obligatorio")
    @Size(min = 1, max = 50, message = "El nombre debe tener entre 1 y 50 caracteres")
    @Pattern(regexp = "^[a-zA-Z0-9 _-]+$", message = "El nombre debe tener entre 1 y 50 caracteres y solo puede contener letras, números, espacios, guiones y guiones bajos")
    private String custom_name;

    @Schema(
        description = "Moneda base de la cuenta (código ISO 4217 de 3 letras)", 
        example = "COP", 
        nullable = false
    )
    @NotBlank(message = "La moneda base de la cuenta es obligatoria")
    private String base_currency;

    @Schema(
        description = "ID del centro de costos asociado a esta cuenta (opcional)", 
        example = "1", 
        nullable = true
    )
    private Long cost_center_id;

    @Schema(
        description = "ID de la regla de depreciación aplicada a esta cuenta (opcional, solo para cuentas de activo fijo)", 
        example = "1", 
        nullable = true
    )
    private Long depreciation_rule_id;

    @Schema(
        description = "Naturaleza contable de la cuenta: define si aumenta por débito o crédito", 
        example = "DEBIT", 
        allowableValues = {"DEBIT", "CREDIT"}, 
        nullable = false
    )
    @NotNull(message = "La naturaleza de la cuenta contable es obligatoria")
    private AccountNature nature;
}
