package com.sigcon.backend.lists_accounting.accounting_account.application;

import java.time.LocalDateTime;

import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountNature;
import com.sigcon.backend.lists_accounting.accounting_account.domain.model.enums.AccountStatus;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.cost_centers.application.CostCenterDTO;
import com.sigcon.backend.lists_accounting.types_of_currency.application.CurrencyTypeResponseDTO;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Respuesta con los datos de una cuenta contable del catálogo para visualización en tablas y formularios")
public class AccountingAccountDTO {

    @Schema(description = "ID único interno de la cuenta contable", example = "1")
    private Long id;

    @Schema(description = "ID del Plan Único de Cuentas (PUC) asociado como categoría padre", example = "1", nullable = true)
    private Long puc_id;

    @Schema(description = "Código oficial de la cuenta según el estándar PUC", example = "1105", nullable = true)
    private ChartOfAccountResponseDTO pucAccount;

    @Schema(description = "Nombre personalizado de la cuenta para visualización en el sistema", example = "Caja General", nullable = true)
    private String customName;

    @Schema(description = "Moneda base de la cuenta", nullable = true)
    private CurrencyTypeResponseDTO currencyType;

    @Schema(description = "Centro de costos asociado a la cuenta", nullable = true)
    private CostCenterDTO costCenter;

    @Schema(description = "ID de la regla tributaria aplicada a esta cuenta", nullable = true)
    private Long taxRuleId;

    @Schema(description = "Naturaleza contable de la cuenta: define si aumenta por débito o crédito", example = "DEBIT", allowableValues = {
            "DEBIT", "CREDIT" })
    private AccountNature nature;

    @Schema(description = "Estado actual de la cuenta en el sistema", example = "ACTIVE", allowableValues = { "ACTIVE",
            "INACTIVE" })
    private AccountStatus status;

    @Schema(description = "Fecha y hora de creación del registro (UTC)", example = "2024-02-21T10:30:00", nullable = true)
    private LocalDateTime createdAt;

    @Schema(description = "Fecha y hora de última actualización del registro (UTC)", example = "2024-02-21T10:30:00", nullable = true)
    private LocalDateTime updatedAt;

    @Schema(description = "Fecha y hora de eliminación lógica del registro (null si la cuenta está activa)", example = "2024-02-21T10:30:00", nullable = true)
    private LocalDateTime deletedAt;
}
