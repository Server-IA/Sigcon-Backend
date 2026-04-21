package com.sigcon.backend.platform.users.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de usuario cross-empresa para HU-PA-PLAT-04.
 *
 * <p>Usado solo por {@code PLATFORM_ADMIN} para listar usuarios de TODAS las
 * empresas. Incluye datos de la empresa (companyId + companyName) para que
 * el admin de plataforma pueda identificar a qué empresa pertenece cada usuario.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Usuario con contexto cross-empresa para vistas de plataforma")
public class PlatformUserDTO {

    @Schema(description = "ID del usuario", example = "15")
    private Long id;

    @Schema(description = "Nombre", example = "Maria")
    private String name;

    @Schema(description = "Apellido", example = "Perez")
    private String lastname;

    @Schema(description = "Email (unico globalmente)", example = "maria@acme.co")
    private String email;

    @Schema(description = "Username (unico globalmente)", example = "maria.perez")
    private String username;

    @Schema(description = "Estado del usuario", example = "ACTIVE")
    private String status;

    @Schema(description = "FK a companies (null si es PLATFORM_ADMIN)", example = "2")
    private Long companyId;

    @Schema(description = "Razon social de la empresa del usuario (null si es PLATFORM_ADMIN)", example = "ACME SAS")
    private String companyName;

    @Schema(description = "Rol de plataforma si aplica (null para tenant users)", example = "PLATFORM_ADMIN")
    private String platformRole;

    @Schema(description = "Lista de nombres de roles asignados")
    private List<String> roles;

    @Schema(description = "Fecha de creacion")
    private LocalDateTime createdAt;
}
