package com.sigcon.backend.lists_accounting.accounting_account.application;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;

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
@Schema(description = "Datos requeridos para actualizar una cuenta contable existente en el catálogo del sistema")
public class UpdateAccountingAccountRequest {

    @Schema(description = "ID único interno de la cuenta contable a actualizar", example = "1", nullable = false)
    @NotNull(message = "El identificador de la cuenta es obligatorio")
    private Long id;

    @Schema(description = "ID del Plan Único de Cuentas (PUC) asociado como categoría padre (solo lectura en actualización)", example = "1", nullable = false)
    @NotNull(message = "El identificador PUC es obligatorio")
    private Long puc_id;

    @Schema(description = "Nombre personalizado de la cuenta para visualización en el sistema", example = "Bancos (1110)", maxLength = 100)
    @NotBlank(message = "El nombre de la cuenta personalizada es obligatorio")
    @Size(min = 1, max = 100, message = "El nombre debe tener entre 1 y 100 caracteres")
    // Permite letras (incluyendo acentos y ñ), numeros, espacios, guiones, parentesis,
    // puntos y comas. Los nombres contables en espaniol comunmente usan parentesis
    // (ej. "Bancos (1110)") y acentos (ej. "Préstamos a corto plazo"). La regla
    // anterior `^[a-zA-Z0-9 _-]+$` rechazaba esos caracteres y bloqueaba la edicion
    // de cuentas auto-provisionadas con esos formatos.
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

    /** HU-CFG-RF-07 E? (Bloque AP, 2026-05-04): regla de depreciacion opcional. */
    @Schema(description = "ID de la regla de depreciacion aplicada a esta cuenta (opcional)", example = "1", nullable = true)
    private Long depretation_rule_id;

    @Schema(description = "Naturaleza contable de la cuenta: define si aumenta por débito o crédito", example = "DEBIT", allowableValues = {
            "DEBIT", "CREDIT" }, nullable = false)
    @NotNull(message = "La naturaleza de la cuenta contable es obligatoria")
    private AccountNature nature;

    @Schema(description = "Estado actual de la cuenta en el sistema", example = "ACTIVE", allowableValues = { "ACTIVE",
            "INACTIVE" }, nullable = false)
    @NotNull(message = "El estado de la cuenta contable es obligatorio")
    private AccountStatus status;
}
