package com.sigcon.backend.banks.cash_management.application;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.sigcon.backend.banks.cash_management.domain.model.enums.AccountingBook;
import com.sigcon.backend.banks.cash_management.domain.model.enums.AuditFrequency;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Datos requeridos para crear una caja")
public class CreateCashRequest {

    @Schema(description = "Codigo unico interno de la Caja de la empresa", example = "CJ-001")
    @NotBlank(message = "el codigo de la caja es obligatorio")
    @Size(max = 20, message = "el codigo no puede superar los 20 caracteres")
    private String cashCode;
    @Schema(description = "Nombre De la caja de la empresa", example = "Caja Principal de la Empresa")
    @NotBlank(message = "el nombre de la caja es obligatorio")
    @Size(max = 100, message = "el nombre de la caja no puede superar los 100 caracteres")
    private String cashName;
    @Schema(description = "Tipo de Caja", example = "GENERAL", allowableValues = {"GENERAL", "PETTY_CASH", "FIXED_FUND"})
    @NotNull(message = "el tipo de caja es obligatorio")
    private CashType cashType;
    @Schema(description = "Descripcion adicional de la caja", example = "Caja Ubicada en la Recepcion de la Empresa")
    @Size(max = 500, message = "la descripcion no puede superar los 500 caracteres")
    private String description;
    @Schema(description = "Ubicacion fisica de la caja", example = "Psio 2, Oficina 201")
    @NotBlank(message = "la ubicacion fisica es obligatoria")
    @Size(max = 200, message = "la ubicacion fisica de la caja no puede superar los 200 caracteres")
    private String physicalLocation;
    @Schema(description = "ID del principla responsable de la caja", example = "1")
    @NotNull(message = "El responsable principal es obligatorio")
    private Long principalResponsibleId;
    @Schema(description = "ID del Responsable alternativo o secundario de la caja(empleado activo,opcional)", example = "1")
    private Long alternateResponsibleId;
    @Schema(description = "Horario de operacion de la caja", example = "08:00-17:00")
    @Size(max = 20, message = "El horario de la operacion no puede superar los 20 caracteres")
    private String operationSchedule;
    @Schema(description = "ID del tipo de moneda de la caja", example = "1")
    @NotNull(message = "La es obligatorio")
    private Long currencyId;
    @Schema(description = "Saldo inical de la caja", example = "1000000.00")
    @NotNull(message = "El saldo inicial es obligatorio")
    @DecimalMin(value = "0.0", message = "El saldo inicial no puede ser negativo")
    private BigDecimal initialBalanace;
    @Schema(description = "Fecha del saldo inicial registrado", example = "2026-01-01")
    @NotNull(message = "La fecha del saldo inicial es obligatoria")
    @PastOrPresent(message = "La fecha del saldo inicial no puede ser futura")
    private LocalDate initialBalanceDay;
    @Schema(description = "Fecha de cracion de la caja", example = "2026-01-01")
    @NotNull(message = "La fecha de creación de la caja es obligatoria")
    @PastOrPresent(message = "La fecha de creación no puede ser futura")
    private LocalDate cashCreationDate;
    @Schema(description = "Limite maximo permitido en la caja", example = "5000000.00")
    @DecimalMin(value = "0.01", message = "El límite máximo debe ser mayor a 0")
    private BigDecimal maxLimit;
    @Schema(description = "Limite minimo requerido en la caja", example = "1000000.00")
    @DecimalMin(value = "0.0", message = "El límite mínimo no puede ser negativo")
    private BigDecimal minLimit;
    @Schema(description = "Indica si la caja requiere autorizacion para el movimiento", example = "true")
    @NotNull(message = "definir si reuqiere autorizacion es obligatorio")
    private Boolean requiresAuthorization;
    @Schema(description = "Monto maximo permitido sin autorizacion", example = "500000.00")
    @DecimalMin(value = "0.0", message = "El monto máximo sin autorización no puede ser negativo")
    private BigDecimal maxAmountWithoutAuthorization;
    @Schema(description = "Monto por el cual se notifica que la caja ha llegado a su limite", example = "150000.00")
    private BigDecimal notifyLimit;
    @Schema(description = "periodicidad del arqueo de la caja", example = "DAILY", allowableValues = {"DAILY","WEEKLY","MONTHLY"})
    @NotNull(message = "La periodicidad del arqueo es obligatoria")
    private AuditFrequency auditFrequency;
    @Schema(description = "ID de la cuenta contable asociada", example = "1")
    @NotNull(message = "La cuenta contable es obligatoria")
    private Long accountingAccountId;
    @Schema(description = "ID del centro de costos asociado")
    private Long costCenterId;
    @Schema(description = "Libro contable de la caja", example = "LOCAL", allowableValues = {"LOCAL", "IFRS", "TAX"})
    @NotNull(message = "El libro contable es obligatorio")
    private AccountingBook accountingBook;
}
