package com.sigcon.backend.assets.assets.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sigcon.backend.assets.assets.domain.model.enums.AssetClassification;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetStatus;
import com.sigcon.backend.assets.assets.domain.model.enums.AssetType;
import com.sigcon.backend.assets.assets.domain.model.enums.DepreciationMethod;
import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;
import com.sigcon.backend.lists_accounting.accounting_lists.application.ChartOfAccountResponseDTO;
import com.sigcon.backend.lists_accounting.depretation_rules.application.DepretationRuleDTO;
import com.sigcon.backend.third_parties.third_parties.application.ThirdPartyDTO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de visualizacion del activo")
public class ViewAssetsDTO {

    @Schema(description = "ID interno del activo", example = "1")
    private Long id;

    @Schema(description = "Codigo unico del activo", example = "ACT2026000001")
    private String assetCode;

    @Schema(description = "Nombre del activo", example = "Impresora laser oficina")
    private String name;

    @Schema(description = "Descripcion funcional", example = "Activo fijo para area administrativa")
    private String description;

    @Schema(description = "Clasificacion del activo", example = "NON_CURRENT")
    private AssetClassification classification;

    @Schema(description = "Tipo de activo", example = "TANGIBLE")
    private AssetType type;

    @Schema(description = "Cuenta contable asociada (DTO completo)")
    private AccountingAccountDTO accountingAccount;

    @Schema(description = "Proveedor asociado (DTO completo)")
    private ThirdPartyDTO supplier;

    @Schema(description = "Valor de adquisicion", example = "3200000.00")
    private BigDecimal acquisitionValue;

    @Schema(description = "Fecha de adquisicion", example = "2026-01-15")
    private LocalDate acquisitionDate;

    @Schema(description = "Vida util en meses", example = "60")
    private Integer usefulLifeMonths;

    @Schema(description = "Regla de depreciacion asociada (DTO completo)")
    private DepretationRuleDTO depretationRule;

    @Schema(description = "Referencia de Cuentas por Pagar (pendiente de integrar)", example = "1001")
    private Long accountsPayableReferenceId;

    @Schema(description = "Referencia de Bancos/Cajas (pendiente de integrar)", example = "5001")
    private Long bankCashReferenceId;

    @Schema(description = "Estado del activo", example = "ACTIVE")
    private AssetStatus status;

    @Schema(description = "Observaciones administrativas", example = "Pendiente de placa interna")
    private String observations;

    @Schema(description = "Usuario creador", example = "admin@sigcon.com")
    private String createdBy;

    @Schema(description = "Usuario que edito por ultima vez", example = "admin@sigcon.com")
    private String updatedBy;

    @Schema(description = "Fecha de creacion", example = "2026-03-06T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de ultima actualizacion", example = "2026-03-06T11:40:10")
    private LocalDateTime updatedAt;
}
