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

    @Schema(description = "ID del Plan Único de Cuentas (PUC) que define la categoría padre de esta cuenta", example = "1", nullable = false)
    @NotNull(message = "El identificador PUC es obligatorio")
    private Long puc_id;

    @Schema(description = "Nombre personalizado de la cuenta para visualización en el sistema", example = "Bancos (1110)", maxLength = 100)
    @NotBlank(message = "El nombre de la cuenta personalizada es obligatorio")
    @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres")
    // Permite letras (incluyendo acentos y ñ), numeros, espacios, guiones, parentesis,
    // puntos y comas. Consistente con UpdateAccountingAccountRequest.
    @Pattern(regexp = "^[a-zA-Z0-9 áéíóúÁÉÍÓÚñÑüÜ()._,\\-]+$",
            message = "El nombre solo puede contener letras, números, espacios, guiones, puntos, comas y paréntesis")
    private String custom_name;

    @Schema(description = "ID del tipo de moneda base de la cuenta", example = "1", nullable = false)
    @NotNull(message = "El tipo de moneda base de la cuenta es obligatorio")
    private Long currency_type_id;

    @Schema(description = "ID del centro de costos asociado a esta cuenta (opcional)", example = "1", nullable = true)
    private Long cost_center_id;

    @Schema(description = "ID de la regla tributaria aplicada a esta cuenta (opcional)", example = "1", nullable = true)
    private Long tax_rule_id;

    @Schema(description = "Naturaleza contable de la cuenta: define si aumenta por débito o crédito", example = "DEBIT", allowableValues = {
            "DEBIT", "CREDIT" }, nullable = false)
    @NotNull(message = "La naturaleza de la cuenta contable es obligatoria")
    private AccountNature nature;
}
