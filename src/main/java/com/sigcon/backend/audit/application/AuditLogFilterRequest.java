package com.sigcon.backend.audit.application;

import com.sigcon.backend.audit.domain.model.enums.AuditAction;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.model.enums.AuditSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Filtros para busqueda avanzada de logs de auditoria (HU-AU-05)")
public class AuditLogFilterRequest {
    @Schema(description = "Filtrar por ID de usuario", example = "1")
    private Long userId;
    @Schema(description = "Filtrar por email (parcial)", example = "superadmin")
    private String userEmail;
    @Schema(description = "Filtrar por accion", example = "CREATE")
    private AuditAction action;
    @Schema(description = "Filtrar por modulo", example = "AP")
    private AuditModule module;
    @Schema(description = "Filtrar por severidad", example = "HIGH")
    private AuditSeverity severity;
    @Schema(description = "Filtrar por tipo de entidad", example = "ThirdParty")
    private String entityType;
    @Schema(description = "Filtrar por ID de entidad", example = "42")
    private Long entityId;
    @Schema(description = "Desde (timestamp)")
    private LocalDateTime dateFrom;
    @Schema(description = "Hasta (timestamp)")
    private LocalDateTime dateTo;
    @Schema(description = "Texto libre en descripcion", example = "factura")
    private String searchText;
}
