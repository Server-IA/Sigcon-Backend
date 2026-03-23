package com.sigcon.backend.banks.cash_management.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sigcon.backend.banks.cash_management.domain.model.enums.AccountingBook;
import com.sigcon.backend.banks.cash_management.domain.model.enums.AuditFrequency;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashStatus;
import com.sigcon.backend.banks.cash_management.domain.model.enums.CashType;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Respuesta con los datos completos de una caja")
public class CashResponse {

    @Schema(description = "ID unico de la Caja de la empresa", example = "1")
    private Long id;
    @Schema(description = "Codigo unico interno de la Caja de la empresa", example = "CJ-001")
    private String cashCode;
    @Schema(description = "Nombre De la caja de la empresa", example = "Caja Principal de la Empresa")
    private String cahsName;
    @Schema(description = "Tipo de Caja", example = "GENERAL")
    private CashType cashType;
    @Schema(description = "Estado de la Caja", example = "ACTIVE")
    private CashStatus cashStatus;
    @Schema(description = "Descripcion adicional de la caja", example = "Caja Ubicada en la Recepcion de la Empresa")
    private String description; 
    @Schema(description = "Ubicacion fisica de la caja", example = "Psio 2, Oficina 201")
    private String physicalLocation; 
    @Schema(description = "ID del principla responsable de la caja", example = "1")
    private Long principalResponsibleId;
    @Schema(description = "Datos del Responsable principal de la caja")
    private ThirdPartyDTO principalResponsible;
    @Schema(description = "ID del Responsable alternativo o secundario de la caja(opcional)", example = "1")
    private Long alternateResponsibleId;
    @Schema(description = "Datos del responsable alternativo o secundario de la caja")
    private ThirdPartyDTO alternateResponsible;
    @Schema(description = "Horario de operacion de la caja", example = "08:00-17:00")
    private String operationSchedule;
    @Schema(description = "ID del tipo de moneda de la caja", example = "1")
    private Long currencyId;
    @Schema(description = "Datos de la moneda asociada")
    private CurrencyTypeResponseDTO currency;
    @Schema(description = "Saldo inical de la caja", example = "1000000.00")
    private BigDecimal initialBalanace;
    @Schema(description = "saldo actual de la caja", example = "950000.00")
    private BigDecimal currentBalance;
    @Schema(description = "Fecha del saldo inicial registrado", example = "2026-01-01")
    private LocalDate initialBalanceDay;
    @Schema(description = "Fecha de cracion de la caja", example = "2026-01-01")
    private LocalDate cashCreationDate;
    @Schema(description = "Limite maximo permitido en la caja", example = "5000000.00")
    private BigDecimal maxLimit;
    @Schema(description = "Limite minimo requerido en la caja", example = "1000000.00")
    private BigDecimal minLimit;
    @Schema(description = "Indica si la caja requiere autorizacion para el movimiento", example = "true")
    private Boolean requiresAuthorization;
    @Schema(description = "Monto maximo permitido sin autorizacion", example = "500000.00")
    private BigDecimal maxAmountWithoutAuthorization;
    @Schema(description = "Monto por el cual se notifica que la caja ha llegado a su limite", example = "150000.00")
    private BigDecimal notifyLimit;
    @Schema(description = "periodicidad del arqueo de la caja", example = "DAILY", allowableValues = {"DAILY", "WEEKLY","MONTHLY"})
    private AuditFrequency auditFrequency;
    @Schema(description = "ID de la cuenta contable asociada", example = "1")
    private Long accountingAccountId;
    @Schema(description = "datos de la cuenta contable asociada")
    private AccountingAccountDTO accountingAccount;
    @Schema(description = "ID del centro de costos asociado")
    private Long costCenterId;
    @Schema(description = "Datos del centro de costos asociado")
    private CostCenterDTO costCenter;
    @Schema(description = "Libro contable de la caja", example = "LOCAL")
    private AccountingBook accountingBook;
    @Schema(description = "Fecha de cierre de la caja (solo aplica si el estado de la caja es CLOSED)", example = "2026-12-31")
    private LocalDate closingDate;
    @Schema(description = "fecha de creacion del registro")
    private LocalDateTime createdAt;
    @Schema(description = "Fecha de actualizacion del registro")
    private LocalDateTime updatedAt;
    @Schema(description = "Fecha de eliminacion del registro (por defecto null si esta activo)")
    private LocalDateTime deletedAt;
}
