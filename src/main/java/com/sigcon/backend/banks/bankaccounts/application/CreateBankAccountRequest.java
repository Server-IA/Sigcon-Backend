package com.sigcon.backend.banks.bankaccounts.application;

import com.sigcon.backend.banks.bankaccounts.domain.model.enums.BankAccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request para crear cuenta bancaria")
public class CreateBankAccountRequest {

    @NotBlank(message = "El código de cuenta es obligatorio")
    @Size(max = 20)
    @Schema(description = "Código único interno legible para la empresa", example = "BANC-001", required = true)
    private String code;

    @NotBlank(message = "El número de cuenta es obligatorio")
    @Size(max = 50)
    @Schema(description = "Número real de la cuenta bancaria", example = "1234567890", required = true)
    private String accountNumber;

    @NotBlank(message = "El nombre de la cuenta es obligatorio")
    @Size(max = 100)
    @Schema(description = "Nombre descriptivo de la cuenta", example = "Cuenta Corriente Principal", required = true)
    private String accountName;

    @NotNull(message = "El tipo de cuenta bancaria es obligatorio")
    @Schema(description = "Tipo de cuenta", example = "CORRIENTE", required = true)
    private BankAccountType accountType;

    @NotNull(message = "El banco es obligatorio")
    @Schema(description = "ID del banco", example = "1", required = true)
    private Long bankId;

    @NotNull(message = "La moneda es obligatoria")
    @Schema(description = "ID del tipo de moneda (ISO 4217)", example = "1", required = true)
    private Long currencyTypeId;

    @NotNull(message = "El saldo inicial es obligatorio")
    @DecimalMin(value = "0", message = "El saldo inicial no puede ser negativo")
    @Schema(description = "Saldo al momento de creación", example = "0.00", required = true)
    private BigDecimal initialBalance;

    @NotNull(message = "La cuenta contable es obligatoria")
    @Schema(description = "ID de la cuenta del plan contable (PUC)", example = "1", required = true)
    private Long chartOfAccountId;

    @NotNull(message = "La empresa es obligatoria")
    @Schema(description = "ID de la empresa propietaria", example = "1", required = true)
    private Long companyId;

    @Schema(description = "ID de la sucursal del banco (opcional)")
    private Long bankBranchId;

    @Size(max = 100)
    @Schema(description = "Nombre de la sucursal donde se abrió la cuenta")
    private String branchName;

    @Size(max = 100)
    @Schema(description = "Nombre del ejecutivo de cuenta")
    private String accountExecutive;

    @Size(max = 20)
    @Pattern(regexp = "^[0-9]{7,20}$", message = "El teléfono debe tener entre 7 y 20 dígitos")
    @Schema(description = "Teléfono de contacto del banco")
    private String bankPhone;

    @Size(max = 500)
    @Schema(description = "Descripción adicional o notas")
    private String description;

    @PastOrPresent(message = "La fecha de apertura no puede ser futura")
    @Schema(description = "Fecha real de apertura de la cuenta")
    private LocalDate openingDate;

    @Schema(description = "Permite saldos negativos (sobregiro)", example = "false")
    private Boolean allowsOverdraft;

    @Schema(description = "Límite de crédito/autorizado (requerido si allowsOverdraft=true)")
    private BigDecimal creditLimit;

    @Schema(description = "Activar alertas de saldo bajo", example = "false")
    private Boolean notifyLowBalance;

    @Schema(description = "Saldo mínimo para alertas (requerido si notifyLowBalance=true)")
    private BigDecimal minimumBalance;

    @Schema(description = "Si maneja chequeras", example = "false")
    private Boolean handlesCheckbook;

    @Schema(description = "ID del centro de costo (opcional)")
    private Long costCenterId;

    @Schema(description = "ID del libro contable. TODO: Integrar con endpoint de búsqueda de libros cuando esté disponible")
    private Long bookId;
}
