package com.sigcon.backend.accounting_lists.application;

import com.sigcon.backend.accounting_lists.domain.model.enums.AccountClass;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountLevel;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountNature;
import com.sigcon.backend.accounting_lists.domain.model.enums.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO de respuesta para una cuenta contable")
public class ChartOfAccountResponseDTO {
    @Schema(description = "ID interno de la cuenta", example = "1")
    private Long id;

    @Schema(description = "Codigo de la cuenta", example = "110505")
    private String code;

    @Schema(description = "Nombre de la cuenta", example = "Caja General")
    private String name;

    @Schema(description = "Clase contable", example = "ASSET")
    private AccountClass accountClass;

    @Schema(description = "Nivel contable", example = "ACCOUNT")
    private AccountLevel level;

    @Schema(description = "Naturaleza contable", example = "DEBIT")
    private AccountNature nature;

    @Schema(description = "Estado de la cuenta", example = "ACTIVE")
    private AccountStatus status;

    @Schema(description = "Fecha de creacion", example = "2026-02-23T10:15:30")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de actualizacion", example = "2026-02-23T10:20:45")
    private LocalDateTime updatedAt;

    @Schema(description = "Fecha de eliminacion logica (si aplica)", example = "2026-02-24T08:00:00", nullable = true)
    private LocalDateTime deletedAt;
}
