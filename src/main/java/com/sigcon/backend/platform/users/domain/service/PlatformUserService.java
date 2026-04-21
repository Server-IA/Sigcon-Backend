package com.sigcon.backend.platform.users.domain.service;

import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.platform.users.application.PlatformUserDTO;
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
            return cb.and(preds.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);

        // Mapear company_id -> nombre para mostrar junto al usuario
        Map<Long, String> companyNames = new HashMap<>();
        companyRepository.findAll().forEach(c -> companyNames.put(c.getId(), c.getBusinessName()));

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
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        if (user.getDeletedAt() != null) {
            throw new IllegalArgumentException("Usuario eliminado");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        log.info("PLATFORM_ADMIN reseteo contrasenia de usuario id={} email={}",
                user.getId(), user.getEmail());
    }
}
