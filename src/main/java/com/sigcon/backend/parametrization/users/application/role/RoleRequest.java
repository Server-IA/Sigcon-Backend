package com.sigcon.backend.parametrization.users.application.role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleRequest {
    private Long id;
    private String name;
    private Set<Long> permissionIds;
    private List<PermissionDTO> permissions;
    private String status;

    /** HU-PA-03 E1 (QA Bloque PA, 2026-05-09) - descripcion textual del rol. */
    private String description;

    /**
     * HU-PA-03 E1 (QA Bloque PA, 2026-05-09) - tipo del rol:
     *  - PREDEFINED: rol predefinido del sistema (CONTADOR, AUDITOR, etc.).
     *  - CUSTOM: rol creado por el ADMIN_EMPRESA.
     *  - GLOBAL: rol global del sistema (PLATFORM_ADMIN, ADMIN, USER).
     * Calculado en el service al serializar; el frontend usa este campo para
     * mostrar el badge visual y filtrar por Tipo.
     */
    private String type;

    /** HU-PA-03 E1: cantidad de usuarios actualmente asignados al rol. */
    private Long assignedUsersCount;

    /** HU-PA-03 E1: timestamp ISO de creacion del rol. */
    private String createdAt;

    /** HU-PA-04 E3: id de la empresa duenia del rol (NULL si es global). */
    private Long companyId;

    /**
     * HU-PA-05 E4 (QA Bloque PA, 2026-05-09): version optimista. El cliente
     * envia el `version` que recibio en el GET; el backend compara con la
     * version actual antes del UPDATE. Si difiere -> HTTP 409.
     */
    private Long version;
}
