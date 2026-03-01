package com.sigcon.backend.lists_accounting.depretation_rules.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationStatus;
import com.sigcon.backend.lists_accounting.depretation_rules.domain.model.enums.DepretationType;

import com.sigcon.backend.lists_accounting.accounting_account.application.AccountingAccountDTO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "Respuesta con los datos de una regla de depreciación")
public class DepretationRuleDTO { 

    @Schema(description = "ID Unico de la regla de depresiación", example = "1")
    private Long id;
    @Schema(description = "Nombre de la regla de depreciación", example = "Depreciación Lineal Equipos") 
    private String name;
    @Schema(description = "Tipo de Depreciacion", example = "LINEAR")
    private DepretationType depretationType;
    @Schema(description = "ID de la Cuenta Contable", example = "1")
    private Long accountingAccountId;
    @Schema(description = "Datos de la cuenta contable asociada")
    private AccountingAccountDTO accountingAccountDTO;
    @Schema(description = "Tasa de depreciacion", example = "20.00")
    private BigDecimal depretationRate;
    @Schema(description = "Vida util del tipo de depreciacion en años", example = "5")
    private Integer usefulLifeYears;
    @Schema(description = "Valor residual del activo al final de la vida util", example = "0.00")
    private BigDecimal residualValue;
    @Schema(description = "Fecha efectiva de vigencia de la regla de depreciacion", example = "01/01/2026")
    private LocalDate effectiveDate;
    @Schema(description = "Descripcion estructurada en formato Texto largo", example = "Base de cálculo: Costo histórico | Parámetros: Tasa 20% | Norma: NIC 16")
    private String descriptionStructured;
    @Schema(description = "Estado de la regla de depreciacion (Activa por defecto)", example = "ACTIVE")
    private DepretationStatus status;
    // auditoria (lo que se supone debe tener las variable que van a auditoria)
    //private Long createdById;
    //private String createdByName; 
    @Schema(description = "Fecha de creación del registro")
    private LocalDateTime createdAt;
    @Schema(description = "Fecha de actualizacion del registro")
    private LocalDateTime updatedAt;
    @Schema(description = "Fecha de eliminacion del registro (por defecto null si esta activo)")
    private LocalDateTime deletedAt;
}
