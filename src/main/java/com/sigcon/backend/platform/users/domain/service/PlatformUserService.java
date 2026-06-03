package com.sigcon.backend.platform.users.domain.service;

import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.service.PasswordPolicyService;
import com.sigcon.backend.parametrization.users.domain.service.SessionService;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.platform.users.application.PlatformUserDTO;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio cross-empresa para HU-PA-PLAT-04.
 *
 * <p><b>Alcance:</b> operaciones globales sobre usuarios que requieren ver
 * o modificar entidades de cualquier empresa. Todas las llamadas DEBEN
 * provenir de un {@code PLATFORM_ADMIN} (la autorizacion se hace a nivel
 * controller).
 *
 * <p><b>Bypass del tenant filter:</b> las consultas de este servicio se
 * ejecutan con {@link TenantContext#isPlatformAdmin()} = true, lo que hace
 * que {@code TenantFilterAspect} NO habilite {@code @Filter("tenantFilter")}
 * y las queries vean usuarios de TODAS las empresas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformUserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditPublisher auditPublisher;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;

    /**
     * HU-PA-PLAT-04 E1/E2/E3: listado global de usuarios con filtros
     * opcionales por empresa, rol de plataforma y estado.
     *
     * @param companyId   si != null, filtra por empresa exacta
     * @param onlyPlatform si true, solo devuelve usuarios con platform_role != null
     * @param status      si != null (ACTIVE/INACTIVE), filtra por estado
     */
    public Page<PlatformUserDTO> search(Long companyId, Boolean onlyPlatform,
                                        String status, Pageable pageable) {
        return search(companyId, onlyPlatform, status, null, pageable);
    }

    /**
     * QA Bloque PA Bug 63 (HU-PA-PLAT-04 E1+E3, 2026-05-09): version enriquecida
     * con filtro adicional roleName + lastLoginAt + activeTemporaryPermissionsCount.
     */
    public Page<PlatformUserDTO> search(Long companyId, Boolean onlyPlatform,
                                        String status, String roleName, Pageable pageable) {
        Specification<User> spec = (root, q, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isNull(root.get("deletedAt")));
            if (companyId != null) {
                preds.add(cb.equal(root.get("companyId"), companyId));
            }
            if (Boolean.TRUE.equals(onlyPlatform)) {
                preds.add(cb.isNotNull(root.get("platformRole")));
            }
            if (status != null && !status.isBlank()) {
                preds.add(cb.equal(root.get("status"), status));
            }
            if (roleName != null && !roleName.isBlank()) {
                jakarta.persistence.criteria.Join<Object, Object> rolesJoin = root.join("roles", jakarta.persistence.criteria.JoinType.LEFT);
                preds.add(cb.equal(cb.upper(rolesJoin.get("name")), roleName.trim().toUpperCase()));
                if (q != null) q.distinct(true);
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);

        Map<Long, String> companyNames = new HashMap<>();
        companyRepository.findAll().forEach(c -> companyNames.put(c.getId(), c.getBusinessName()));

        // QA Bloque PA Bug 63 (HU-PA-PLAT-04 E1): enriquecer con lastLogin + tempPermCount
        // batch (una sola query para todos los user ids en la pagina)
        java.util.List<Long> userIds = page.getContent().stream().map(User::getId).collect(java.util.stream.Collectors.toList());
        Map<Long, java.time.LocalDateTime> lastLogins = new HashMap<>();
        Map<Long, Long> tempPermCounts = new HashMap<>();
        if (!userIds.isEmpty()) {
            try {
                org.springframework.jdbc.core.RowCallbackHandler loginHandler = rs -> {
                    long uid = rs.getLong(1);
                    java.sql.Timestamp ts = rs.getTimestamp(2);
                    if (ts != null) lastLogins.put(uid, ts.toLocalDateTime());
                };
                jdbcTemplate.query(
                    "SELECT user_id, MAX(timestamp) FROM audit_logs WHERE action='LOGIN' AND user_id = ANY (?) GROUP BY user_id",
                    loginHandler,
                    new Object[]{userIds.toArray(new Long[0])});
            } catch (Exception ignored) { /* tabla audit_logs puede tener nombres distintos en BDs antiguas */ }
            try {
                org.springframework.jdbc.core.RowCallbackHandler tpHandler = rs ->
                    tempPermCounts.put(rs.getLong(1), rs.getLong(2));
                jdbcTemplate.query(
                    "SELECT user_id, COUNT(*) FROM temporary_permissions WHERE status='ACTIVE' AND deleted_at IS NULL AND user_id = ANY (?) GROUP BY user_id",
                    tpHandler,
                    new Object[]{userIds.toArray(new Long[0])});
            } catch (Exception ignored) {}
        }

        return page.map(u -> PlatformUserDTO.builder()
                .id(u.getId())
                .name(u.getName())
                .lastname(u.getLastname())
                .email(u.getEmail())
                .username(u.getUsername())
                .status(u.getStatus() != null ? u.getStatus().name() : null)
                .companyId(u.getCompanyId())
                .companyName(u.getCompanyId() != null ? companyNames.get(u.getCompanyId()) : null)
                .platformRole(u.getPlatformRole())
                .roles(u.getRoles() == null ? List.of()
                        : u.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()))
                .createdAt(u.getCreatedAt())
                .lastLoginAt(lastLogins.get(u.getId()))
                .activeTemporaryPermissionsCount(tempPermCounts.getOrDefault(u.getId(), 0L))
                .build());
    }

    /**
     * HU-PA-PLAT-04 E4: resetea la contrasenia de un usuario. Solo PLATFORM_ADMIN.
     *
     * <p>La nueva contrasenia se codifica con BCrypt. El cambio aplica
     * inmediatamente; el usuario debera loguearse con la nueva contrasenia.
     *
     * @throws IllegalArgumentException si el usuario no existe o esta eliminado
     */
    /**
     * QA Bloque PA Bug 65 (HU-PA-PLAT-07 E1, 2026-05-09): crear PLATFORM_ADMIN secundario.
     */
    @Transactional
    public PlatformUserDTO createPlatformAdmin(String name, String lastname, String email,
                                                String username, String password) {
        if (email == null || email.isBlank() || username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new IllegalArgumentException("name/email/username/password son obligatorios");
        }
        // PA-RF-01 punto 3 / PA-RF-PLAT-07 (v3.0): la contrasena del nuevo
        // PLATFORM_ADMIN debe cumplir la politica de seguridad.
        passwordPolicyService.validateComplexity(password);
        // HU-PA-PLAT-07 E2: email unico cross-tenant
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            // E2: si el email ya existe en una empresa, mensaje exclusividad
            User existing = userRepository.findByEmail(email).orElse(null);
            if (existing != null && existing.getCompanyId() != null) {
                throw new IllegalArgumentException(
                    "Ya existe un usuario con ese email en una empresa. "
                  + "Un usuario no puede ser simultaneamente de plataforma y de empresa");
            }
            throw new IllegalArgumentException("Ya existe un usuario con ese email en el sistema");
        }
        if (userRepository.findByUsernameOrEmail(username, email).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con ese username");
        }
        User u = User.builder()
                .name(name)
                .lastname(lastname)
                .email(email.trim().toLowerCase())
                .username(username.trim())
                .password(passwordEncoder.encode(password))
                .status(com.sigcon.backend.parametrization.users.domain.model.enums.Status.ACTIVE)
                .companyId(null)
                .platformRole("PLATFORM_ADMIN")
                .build();
        User saved = userRepository.save(u);
        // PA-RF-01: registrar la primera contrasena en el historial.
        passwordPolicyService.record(saved.getId(), saved.getPassword());
        log.info("PLATFORM_ADMIN secundario creado: id={} email={}", saved.getId(), saved.getEmail());
        auditPublisher.publishCreate(AuditModule.PA, "User", saved.getId(),
                "PLATFORM_ADMIN secundario creado: " + saved.getEmail());
        return toDto(saved);
    }

    /**
     * QA Bloque PA Bug 65 (HU-PA-PLAT-07 E3): editar campos basicos del PLATFORM_ADMIN.
     * NO permite cambiar el flag platform_role (eso seria convertirlo en tenant user).
     */
    @Transactional
    public PlatformUserDTO updatePlatformAdmin(Long userId, String name, String lastname, String email) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (u.getDeletedAt() != null) throw new IllegalArgumentException("Usuario eliminado");
        if (u.getPlatformRole() == null) {
            throw new IllegalArgumentException("El usuario no es PLATFORM_ADMIN");
        }
        if (email != null && !email.isBlank() && !email.equalsIgnoreCase(u.getEmail())) {
            // PA-RF-PLAT-07 punto 4 (v3.0): al cambiar el correo de un PLATFORM_ADMIN
            // se valida unicidad GLOBAL del email, incluyendo usuarios inactivos y
            // eliminados logicamente (mismo criterio que PA-RF-09 punto 5). No se
            // permite reutilizar emails de cuentas soft-deleted.
            if (userRepository.existsByEmailIncludingDeleted(email, userId)) {
                throw new IllegalArgumentException(
                        "Ya existe un usuario con ese email en el sistema (incluye cuentas inactivas o eliminadas)");
            }
            u.setEmail(email.trim().toLowerCase());
        }
        if (name != null && !name.isBlank()) u.setName(name);
        if (lastname != null && !lastname.isBlank()) u.setLastname(lastname);
        userRepository.save(u);
        auditPublisher.publishUpdate(AuditModule.PA, "User", u.getId(),
                "PLATFORM_ADMIN actualizado: " + u.getEmail());
        return toDto(u);
    }

    /**
     * QA Bloque PA Bug 65 (HU-PA-PLAT-07 E4): desactivar PLATFORM_ADMIN con safeguard.
     */
    /** Wrapper legacy sin motivo. */
    @Transactional
    public void deactivatePlatformAdmin(Long userId) {
        deactivatePlatformAdmin(userId, "(legacy: sin motivo)");
    }

    /**
     * PA-RF-PLAT-07 v3.0 (Control de Cambios PA, 2026-05-29): desactivar
     * PLATFORM_ADMIN con motivo + safeguard del ultimo activo + efecto inmediato
     * sobre sus sesiones (punto 5).
     */
    @Transactional
    public void deactivatePlatformAdmin(Long userId, String reason) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (u.getPlatformRole() == null) {
            throw new IllegalArgumentException("El usuario no es PLATFORM_ADMIN");
        }
        // HU-PA-PLAT-07 E4: safeguard del ultimo PLATFORM_ADMIN activo.
        long activeOthers = userRepository.findAll((root, q, cb) -> cb.and(
            cb.isNotNull(root.get("platformRole")),
            cb.equal(root.get("status"), com.sigcon.backend.parametrization.users.domain.model.enums.Status.ACTIVE),
            cb.notEqual(root.get("id"), userId),
            cb.isNull(root.get("deletedAt"))
        )).size();
        if (activeOthers == 0) {
            throw new IllegalStateException(
                "Debe existir al menos un PLATFORM_ADMIN activo en la plataforma. "
              + "Cree otro antes de desactivar este");
        }
        u.setStatus(com.sigcon.backend.parametrization.users.domain.model.enums.Status.INACTIVE);
        // PA-RF-PLAT-07 punto 5: efecto inmediato sobre las sesiones activas.
        u.setSessionInvalidatedAt(java.time.LocalDateTime.now());
        userRepository.save(u);
        int revoked = sessionService.revokeAllForUser(userId);
        log.info("PLATFORM_ADMIN desactivado: id={} email={} ({} sesiones revocadas) reason={}",
                u.getId(), u.getEmail(), revoked, reason);
        auditPublisher.publishUpdate(AuditModule.PA, "User", u.getId(),
                "PLATFORM_ADMIN desactivado: " + u.getEmail()
              + " | invalidatedSessions=" + revoked + " | reason=" + reason);
    }

    /** PA-RF-PLAT-07 v3.0: reactivar un PLATFORM_ADMIN previamente desactivado. */
    @Transactional
    public PlatformUserDTO activatePlatformAdmin(Long userId, String reason) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (u.getPlatformRole() == null) {
            throw new IllegalArgumentException("El usuario no es PLATFORM_ADMIN");
        }
        u.setStatus(com.sigcon.backend.parametrization.users.domain.model.enums.Status.ACTIVE);
        userRepository.save(u);
        log.info("PLATFORM_ADMIN reactivado: id={} email={}", u.getId(), u.getEmail());
        auditPublisher.publishUpdate(AuditModule.PA, "User", u.getId(),
                "PLATFORM_ADMIN reactivado: " + u.getEmail() + " | reason=" + reason);
        return toDto(u);
    }

    /** PA-RF-PLAT-07 v3.0: consultar un usuario (PLATFORM_ADMIN o de empresa) por id. */
    @Transactional(readOnly = true)
    public PlatformUserDTO getPlatformAdmin(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        PlatformUserDTO dto = toDto(u);
        if (u.getCompanyId() != null) {
            companyRepository.findById(u.getCompanyId())
                    .ifPresent(c -> dto.setCompanyName(c.getBusinessName()));
        }
        return dto;
    }

    private PlatformUserDTO toDto(User u) {
        return PlatformUserDTO.builder()
                .id(u.getId())
                .name(u.getName())
                .lastname(u.getLastname())
                .email(u.getEmail())
                .username(u.getUsername())
                .status(u.getStatus() != null ? u.getStatus().name() : null)
                .companyId(u.getCompanyId())
                .companyName(null)
                .platformRole(u.getPlatformRole())
                .roles(java.util.List.of())
                .createdAt(u.getCreatedAt())
                .build();
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException("Usuario eliminado");
        }
        // PA-RF-01 punto 3 (v3.0): politica de seguridad + no reutilizar ultimas 5.
        passwordPolicyService.assertAllowedForUser(user.getId(), user.getPassword(), newPassword);
        String encoded = passwordEncoder.encode(newPassword);
        user.setPassword(encoded);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        // PA-RF-PLAT-07 punto 5 (v3.0): efecto inmediato sobre las sesiones.
        user.setSessionInvalidatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        passwordPolicyService.record(user.getId(), encoded);
        int revokedSessions = sessionService.revokeAllForUser(user.getId());
        log.info("PLATFORM_ADMIN reseteo contrasenia de usuario id={} email={} ({} sesiones revocadas)",
                user.getId(), user.getEmail(), revokedSessions);
        // HU-PA-PLAT-04 E4: evidencia inmutable del cambio de credenciales.
        // Se registra en la bitacora de la empresa a la que pertenece el usuario
        // (o sin tenant si es un PLATFORM_ADMIN, donde el log caera en el tenant
        // del admin ejecutor o null si este es tambien PLATFORM_ADMIN).
        Long targetCompanyId = user.getCompanyId();
        Runnable audit = () -> auditPublisher.publishUpdate(AuditModule.PA, "User", user.getId(),
                "Contrasenia reseteada por PLATFORM_ADMIN para usuario " + user.getUsername()
                        + " (" + user.getEmail() + ")");
        if (targetCompanyId != null) {
            TenantContext.runAs(targetCompanyId, false, audit);
        } else {
            audit.run();
        }
    }
}
