package com.sigcon.backend.platform.companies.domain.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.enums.Status;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.RoleRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.service.PasswordPolicyService;
import com.sigcon.backend.parametrization.users.domain.service.SessionService;
import com.sigcon.backend.platform.companies.application.CompanyDTO;
import com.sigcon.backend.platform.companies.application.CreateCompanyRequest;
import com.sigcon.backend.platform.companies.application.CreateCompanyWithAdminRequest;
import com.sigcon.backend.platform.companies.application.UpdateCompanyRequest;
import com.sigcon.backend.platform.companies.domain.model.Company;
import com.sigcon.backend.platform.companies.domain.model.Company.CompanyStatus;
import com.sigcon.backend.platform.companies.domain.repository.CompanyRepository;
import com.sigcon.backend.platform.tenant.TenantContext;
import com.sigcon.backend.audit.domain.model.enums.AuditModule;
import com.sigcon.backend.audit.domain.service.AuditPublisher;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio de administracion de empresas (tenants). Solo {@code PLATFORM_ADMIN}
 * lo usa — el aislamiento lo garantiza el {@code @PreAuthorize} del controller.
 *
 * <p><b>Auto-provision al crear una empresa</b> (HU-TENANT-04 + HU-TENANT-05):
 * cada {@link #create} invoca {@link #provisionTenantDefaults} que delega en la
 * funcion PL/pgSQL {@code _tenant_auto_provision} (definida en V10-D). La funcion
 * siembra idempotentemente:
 * <ul>
 *   <li>12 periodos contables OPEN del anio actual.</li>
 *   <li>18 mapeos contables PUC Colombia (AR_CLIENTES=1305, AP_PROVEEDORES=2205, ...).</li>
 *   <li>1 centro de costo default {@code CC-DEFAULT}.</li>
 * </ul>
 *
 * <p><b>Alta atomica con primer admin</b> (HU-PLAT-02): {@link #createWithAdmin}
 * combina la creacion de la empresa + User(role=ADMIN, company_id=nueva) en UNA
 * transaccion. Si falla cualquiera, rollback total — sin empresas huerfanas.
 *
 * <p><b>Desactivacion</b> (HU-PLAT-05): {@link #deactivate} marca status=INACTIVE;
 * los tenant users de esa empresa no pueden loguearse hasta {@link #activate}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditPublisher auditPublisher;
    private final PasswordPolicyService passwordPolicyService;
    private final SessionService sessionService;

    /**
     * Publica un audit log de una operacion a nivel de plataforma sobre una empresa
     * target, escribiendolo en la bitacora de ESA empresa. Usa TenantContext.runAs
     * para propagar el company_id al @PrePersist de AuditLog.
     */
    private void auditCompany(Long targetCompanyId, String action, String description) {
        TenantContext.runAs(targetCompanyId, false, () -> {
            switch (action) {
                case "CREATE" -> auditPublisher.publishCreate(AuditModule.PA, "Company", targetCompanyId, description);
                case "UPDATE" -> auditPublisher.publishUpdate(AuditModule.PA, "Company", targetCompanyId, description);
                case "DELETE" -> auditPublisher.publishDelete(AuditModule.PA, "Company", targetCompanyId, description);
                default -> auditPublisher.publishUpdate(AuditModule.PA, "Company", targetCompanyId, description);
            }
        });
    }

    /** HU-PLAT-01 E1: listado paginado de empresas (incluye INACTIVE). */
    @Transactional(readOnly = true)
    public Page<CompanyDTO> findAll(Pageable pageable) {
        return companyRepository.findAll(pageable).map(CompanyDTO::from);
    }

    /**
     * QA Bloque PA Bug 57 (HU-PA-PLAT-02 E1+E2+E3, 2026-05-09): listado con
     * filtros opcionales de status + nit prefix. Si enrich=true, agrega
     * activeUsersCount + openPeriodsCount + aaefStatus a cada DTO.
     */
    @Transactional(readOnly = true)
    public Page<CompanyDTO> findAll(Pageable pageable, String status, String nitPrefix, boolean enrich) {
        org.springframework.data.jpa.domain.Specification<Company> spec = (root, q, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            preds.add(cb.isNull(root.get("deletedAt")));
            if (status != null && !status.isBlank()) {
                try {
                    preds.add(cb.equal(root.get("status"), CompanyStatus.valueOf(status.toUpperCase())));
                } catch (IllegalArgumentException ignored) { /* status invalido = no filtrar */ }
            }
            if (nitPrefix != null && !nitPrefix.isBlank()) {
                preds.add(cb.like(root.get("nit"), nitPrefix.trim() + "%"));
            }
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<Company> page = companyRepository.findAll(spec, pageable);
        return page.map(c -> {
            CompanyDTO dto = CompanyDTO.from(c);
            if (enrich) enrichCompanyMetrics(dto, c.getId(), false);
            return dto;
        });
    }

    /**
     * QA Bloque PA Bug 57 (HU-PA-PLAT-02 E4, 2026-05-09): popular metricas en el DTO
     * para listado/detalle. {@code includeDetail=true} agrega usersByRole,
     * periodsByStatus y aaefLastBatch (mas costoso).
     */
    private void enrichCompanyMetrics(CompanyDTO dto, Long companyId, boolean includeDetail) {
        try {
            Number activeUsers = (Number) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE company_id=? AND deleted_at IS NULL",
                Number.class, companyId);
            dto.setActiveUsersCount(activeUsers == null ? 0L : activeUsers.longValue());

            int year = LocalDate.now().getYear();
            Number openPeriods = (Number) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounting_periods WHERE company_id=? AND year=? AND status='OPEN'",
                Number.class, companyId, year);
            dto.setOpenPeriodsCount(openPeriods == null ? 0L : openPeriods.longValue());

            Number aaefBatches = (Number) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_batches WHERE company_id=? AND deleted_at IS NULL",
                Number.class, companyId);
            dto.setAaefBatchesCount(aaefBatches == null ? 0L : aaefBatches.longValue());

            Number ackFailed = (Number) jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM integration_batches WHERE company_id=? AND status='ACK_FAILED' AND deleted_at IS NULL",
                Number.class, companyId);
            dto.setAaefStatus((ackFailed != null && ackFailed.longValue() > 0) ? "ERROR" : "OK");

            if (includeDetail) {
                @SuppressWarnings("unchecked")
                java.util.List<Object[]> rolesRows = jdbcTemplate.query(
                    "SELECT r.name, COUNT(DISTINCT u.id) FROM users u "
                  + "JOIN users_roles ur ON ur.user_id=u.id "
                  + "JOIN roles r ON r.id=ur.role_id "
                  + "WHERE u.company_id=? AND u.deleted_at IS NULL AND r.deleted_at IS NULL "
                  + "GROUP BY r.name ORDER BY r.name",
                    (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)},
                    companyId);
                java.util.Map<String, Long> usersByRole = new java.util.LinkedHashMap<>();
                for (Object[] row : rolesRows) usersByRole.put((String) row[0], (Long) row[1]);
                dto.setUsersByRole(usersByRole);

                @SuppressWarnings("unchecked")
                java.util.List<Object[]> periodsRows = jdbcTemplate.query(
                    "SELECT status, COUNT(*) FROM accounting_periods WHERE company_id=? GROUP BY status",
                    (rs, n) -> new Object[]{rs.getString(1), rs.getLong(2)},
                    companyId);
                java.util.Map<String, Long> periodsByStatus = new java.util.LinkedHashMap<>();
                for (Object[] row : periodsRows) periodsByStatus.put((String) row[0], (Long) row[1]);
                dto.setPeriodsByStatus(periodsByStatus);

                java.util.List<Object[]> lastBatchRows = jdbcTemplate.query(
                    "SELECT id, received_at FROM integration_batches WHERE company_id=? AND deleted_at IS NULL ORDER BY received_at DESC LIMIT 1",
                    (rs, n) -> new Object[]{rs.getLong(1), rs.getTimestamp(2)},
                    companyId);
                if (!lastBatchRows.isEmpty()) {
                    Object[] row = lastBatchRows.get(0);
                    dto.setAaefLastBatchId((Long) row[0]);
                    java.sql.Timestamp ts = (java.sql.Timestamp) row[1];
                    if (ts != null) dto.setAaefLastBatchAt(ts.toLocalDateTime());
                }

                java.util.List<java.sql.Timestamp> lastLoginRows = jdbcTemplate.query(
                    "SELECT MAX(timestamp) FROM audit_logs WHERE company_id=? AND action='LOGIN'",
                    (rs, n) -> rs.getTimestamp(1),
                    companyId);
                if (!lastLoginRows.isEmpty() && lastLoginRows.get(0) != null) {
                    dto.setLastLoginAt(lastLoginRows.get(0).toLocalDateTime());
                }
            }
        } catch (Exception ex) {
            log.warn("enrichCompanyMetrics fallo para companyId={}: {}", companyId, ex.getMessage());
        }
    }

    /** Solo empresas ACTIVE. Util para dropdowns y combos del frontend. */
    @Transactional(readOnly = true)
    public List<CompanyDTO> findAllActive() {
        return companyRepository.findByStatusAndDeletedAtIsNull(CompanyStatus.ACTIVE)
                .stream().map(CompanyDTO::from).toList();
    }

    /** HU-PLAT-01 E2: detalle de empresa por id. Lanza si no existe. */
    @Transactional(readOnly = true)
    public CompanyDTO findById(Long id) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        // QA Bloque PA Bug 57 (HU-PA-PLAT-02 E4): enriquecer con metricas + usersByRole + aaefLastBatch
        CompanyDTO dto = CompanyDTO.from(c);
        enrichCompanyMetrics(dto, id, true);
        return dto;
    }

    /**
     * HU-PLAT-01 E3: crea una empresa vacia (sin admin). Valida NIT unico y
     * dispara auto-provision de periodos/mapeos/cost-center.
     *
     * @throws IllegalArgumentException si el NIT ya esta registrado en otra empresa activa
     */
    @Transactional
    public CompanyDTO create(CreateCompanyRequest request) {
        // Validacion de NIT + DV (HU-PA-10 E2 + HU-PA-PLAT-01 E4). Extraida a
        // metodo reutilizable para compartirla con createWithAdmin (v3.0).
        validateNitForCreate(request);

        Company c = Company.builder()
                .nit(request.getNit())
                .dv(Optional.ofNullable(request.getDv()).orElse("0"))
                .businessName(request.getBusinessName())
                .legalRepresentative(request.getLegalRepresentative())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .companySize(request.getCompanySize())
                .typeOrganizationId(request.getTypeOrganizationId())
                .typeRegimenId(request.getTypeRegimenId())
                .plan(request.getPlan())                      // PA-RF-10 punto 5
                .regionalConfig(request.getRegionalConfig())  // PA-RF-10 punto 5
                .status(CompanyStatus.ACTIVE)
                .build();

        c = companyRepository.save(c);
        log.info("Empresa creada: id={}, nit={}, businessName='{}'",
                c.getId(), c.getNit(), c.getBusinessName());
        auditCompany(c.getId(), "CREATE",
                "Empresa creada: " + c.getBusinessName() + " (NIT " + c.getNit() + ")");

        // HU-TENANT-04 + HU-TENANT-05: auto-provisionar al crear empresa:
        // 12 periodos del ano actual + 18 mapeos contables default + 1 cost center.
        // Delega en la funcion PL/pgSQL _tenant_auto_provision (V10-D).
        provisionTenantDefaults(c.getId(), LocalDate.now().getYear());

        return CompanyDTO.from(c);
    }

    /**
     * HU-TENANT-04/05: invoca la funcion PL/pgSQL que siembra periodos, mapeos PUC y
     * centro de costo default. Idempotente: si ya existen, no crea duplicados.
     */
    @Transactional
    public void provisionTenantDefaults(Long companyId, int year) {
        jdbcTemplate.queryForObject(
                "SELECT _tenant_auto_provision(?, ?)",
                Object.class,
                companyId, year);
        // V9-ZZG: clonar 5 reglas de riesgo + 4 politicas de retencion baseline
        // desde SIGCON DEMO (id=1). Idempotente: skip si ya tiene.
        try {
            jdbcTemplate.queryForObject(
                    "SELECT _tenant_seed_audit_baseline(?)",
                    Object.class,
                    companyId);
        } catch (Exception e) {
            // Funcion puede no existir en BDs antiguas (pre V9-ZZG). No bloqueamos creacion.
            log.warn("_tenant_seed_audit_baseline fallo o no existe para companyId={}: {}",
                     companyId, e.getMessage());
        }
        // QA Bloque PA Bug 24 (HU-PA-10 E1, 2026-05-09): aprovisionar los 6 roles
        // predefinidos en la empresa nueva (CONTADOR, AUXILIAR_CONTABLE, AUDITOR,
        // ADMIN_EMPRESA, TESORERO, OPERADOR_NOMINA) con sus permisos clonados de
        // SIGCON DEMO. La funcion V9-ZZZZC es idempotente.
        try {
            jdbcTemplate.queryForObject(
                    "SELECT _seed_predefined_roles_for_tenant(?)",
                    Object.class,
                    companyId);
            log.info("HU-PA-10 E1: roles predefinidos aprovisionados para companyId={}", companyId);
        } catch (Exception e) {
            // Si falla en BDs antiguas (pre V9-ZZZZC), advertir pero no bloquear.
            // El backfill de la propia migracion los crea; solo afectaria a empresas
            // creadas en esa ventana.
            log.error("_seed_predefined_roles_for_tenant fallo para companyId={}: {}",
                     companyId, e.getMessage());
            throw new IllegalStateException(
                    "Error al aprovisionar la empresa. Intente nuevamente. Si persiste, contacte soporte tecnico.", e);
        }
        log.info("Tenant auto-provision completado: companyId={}, year={}", companyId, year);
    }

    @Transactional
    public CompanyDTO update(Long id, UpdateCompanyRequest request) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        // Si cambia NIT, validar unicidad contra otras empresas activas
        if (request.getNit() != null && !request.getNit().isBlank()
                && !request.getNit().equals(c.getNit())) {
            if (companyRepository.existsByNitAndIdNotAndDeletedAtIsNull(request.getNit(), id)) {
                throw new IllegalArgumentException(
                        "Ya existe otra empresa con el NIT " + request.getNit());
            }
            // TODO (Bloque D): si la empresa ya tiene movimientos contables
            // registrados, bloquear cambio de NIT (riesgo fiscal — HU-PA-RF-61 E2).
            c.setNit(request.getNit());
        }
        if (request.getDv() != null)                   c.setDv(request.getDv());
        if (request.getBusinessName() != null)         c.setBusinessName(request.getBusinessName());
        if (request.getLegalRepresentative() != null)  c.setLegalRepresentative(request.getLegalRepresentative());
        if (request.getEmail() != null)                c.setEmail(request.getEmail());
        if (request.getPhone() != null)                c.setPhone(request.getPhone());
        if (request.getAddress() != null)              c.setAddress(request.getAddress());
        if (request.getCompanySize() != null)          c.setCompanySize(request.getCompanySize());
        if (request.getTypeOrganizationId() != null)   c.setTypeOrganizationId(request.getTypeOrganizationId());
        if (request.getTypeRegimenId() != null)        c.setTypeRegimenId(request.getTypeRegimenId());

        c = companyRepository.save(c);
        log.info("Empresa actualizada: id={}", c.getId());
        auditCompany(c.getId(), "UPDATE",
                "Empresa actualizada: " + c.getBusinessName() + " (NIT " + c.getNit() + ")");
        return CompanyDTO.from(c);
    }

    /**
     * HU-PLAT-02 / PA-RF-PLAT-01 v3.0 (Control de Cambios PA, 2026-05-29): crea
     * empresa + primer usuario ADMIN_EMPRESA con MAQUINA DE ESTADOS de
     * aprovisionamiento:
     * <ol>
     *   <li>Idempotencia: si llega un request con la misma Idempotency-Key ya
     *       procesada, se devuelve el resultado original sin reprocesar (punto 2).</li>
     *   <li>La empresa nace en estado PROVISIONING con un provisioningId unico (puntos 1/3).</li>
     *   <li>Si todo el aprovisionamiento (recursos base + 9 roles + admin) termina
     *       OK -> pasa a ACTIVE.</li>
     *   <li>Si falla -> la empresa queda en estado ERROR (no solo rollback, punto 4),
     *       visible para el PLATFORM_ADMIN para re-provisionar o eliminar.</li>
     *   <li>La contrasena del primer admin se valida contra la politica de seguridad (punto 6).</li>
     * </ol>
     * El metodo NO es @Transactional: orquesta varias transacciones independientes
     * (via {@code self()}) para poder dejar la empresa en ERROR si el aprovisionamiento
     * falla DESPUES de haberla creado.
     */
    public CompanyDTO createWithAdmin(CreateCompanyWithAdminRequest req) {
        return createWithAdmin(req, null);
    }

    /** Variante con Idempotency-Key (PA-RF-PLAT-01 punto 2). */
    public CompanyDTO createWithAdmin(CreateCompanyWithAdminRequest req, String idempotencyKey) {
        // PA-RF-PLAT-01 punto 2: idempotencia. Si esta key ya se proceso, devolver
        // el resultado original sin crear nada nuevo.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Company> existing =
                    companyRepository.findByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey.trim());
            if (existing.isPresent()) {
                Company c = existing.get();
                CompanyDTO dto = CompanyDTO.from(c);
                userRepository.findAllByRoles_Name("ADMIN_EMPRESA").stream()
                        .filter(u -> c.getId().equals(u.getCompanyId()))
                        .findFirst()
                        .ifPresent(u -> {
                            dto.setAdminUserId(u.getId());
                            dto.setAdminEmail(u.getEmail());
                            dto.setAdminUsername(u.getUsername());
                        });
                log.info("PLAT-01 idempotente: Idempotency-Key ya procesada, companyId={}", c.getId());
                return dto;
            }
        }

        // Validaciones de unicidad ANTES de crear nada (para no dejar una empresa en
        // ERROR por un duplicado evitable).
        if (userRepository.existsByEmailAndDeletedAtIsNull(req.getAdminEmail())) {
            throw new IllegalArgumentException("Ya existe un usuario con el email " + req.getAdminEmail());
        }
        if (userRepository.findByUsernameOrEmail(req.getAdminUsername(), req.getAdminEmail()).isPresent()) {
            throw new IllegalArgumentException("Ya existe un usuario con el username " + req.getAdminUsername());
        }
        // PA-RF-PLAT-01 punto 5 (v3.0): los datos legales de la empresa son
        // obligatorios en el alta atomica: direccion principal, correo corporativo
        // y telefono principal. (El alta simple sin admin los mantiene opcionales.)
        validateCompanyLegalData(req.getCompany());
        // PA-RF-PLAT-01 punto 6: la contrasena del primer admin cumple la politica de seguridad.
        passwordPolicyService.validateComplexity(req.getAdminPassword());
        // Validar NIT/DV antes de crear (mismo criterio que create()).
        validateNitForCreate(req.getCompany());

        // TX1 (committed): crear la empresa en PROVISIONING con provisioningId.
        Company company = self().createCompanyInProvisioning(req.getCompany(), idempotencyKey);
        Long companyId = company.getId();
        String provisioningId = company.getProvisioningId();

        try {
            // TX2 (committed): aprovisionar recursos base + 9 roles + crear admin.
            Long adminId = self().provisionAndCreateAdmin(companyId, req);
            // TX3 (committed): marcar ACTIVE.
            self().markCompanyActive(companyId);

            Company refreshed = companyRepository.findById(companyId).orElse(company);
            CompanyDTO dto = CompanyDTO.from(refreshed);
            dto.setAdminUserId(adminId);
            dto.setAdminEmail(req.getAdminEmail());
            dto.setAdminUsername(req.getAdminUsername());
            return dto;
        } catch (Exception ex) {
            // PA-RF-PLAT-01 punto 4: ante fallo, dejar la empresa en ERROR (no solo
            // rollback). Asi el PLATFORM_ADMIN la ve y decide re-provisionar o borrar.
            self().markCompanyError(companyId, ex.getMessage());
            log.error("PLAT-01: aprovisionamiento fallo para companyId={} provisioningId={}: {}",
                    companyId, provisioningId, ex.getMessage(), ex);
            throw new IllegalStateException(
                    "El aprovisionamiento de la empresa (id=" + companyId + ", provisioningId="
                  + provisioningId + ") fallo y la empresa quedo en estado ERROR. Detalle: " + ex.getMessage(), ex);
        }
    }

    /** TX1 del saga PLAT-01: crea la empresa en estado PROVISIONING. */
    @Transactional
    public Company createCompanyInProvisioning(CreateCompanyRequest request, String idempotencyKey) {
        Company c = Company.builder()
                .nit(request.getNit())
                .dv(Optional.ofNullable(request.getDv()).orElse("0"))
                .businessName(request.getBusinessName())
                .legalRepresentative(request.getLegalRepresentative())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .companySize(request.getCompanySize())
                .typeOrganizationId(request.getTypeOrganizationId())
                .typeRegimenId(request.getTypeRegimenId())
                .plan(request.getPlan())
                .regionalConfig(request.getRegionalConfig())
                .provisioningId(java.util.UUID.randomUUID().toString())
                .idempotencyKey((idempotencyKey == null || idempotencyKey.isBlank()) ? null : idempotencyKey.trim())
                .status(CompanyStatus.PROVISIONING)
                .build();
        c = companyRepository.save(c);
        auditCompany(c.getId(), "CREATE",
                "Empresa creada en PROVISIONING: " + c.getBusinessName() + " (NIT " + c.getNit()
              + ", provisioningId=" + c.getProvisioningId() + ")");
        return c;
    }

    /** TX2 del saga PLAT-01: aprovisiona recursos base + 9 roles + crea el admin inicial. */
    @Transactional
    public Long provisionAndCreateAdmin(Long companyId, CreateCompanyWithAdminRequest req) {
        // HU-TENANT-04/05 + PA-RF-10: periodos + mapeos + cost-center + 9 roles predefinidos.
        provisionTenantDefaults(companyId, LocalDate.now().getYear());

        Role adminRole = roleRepository
                .findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull("ADMIN_EMPRESA", companyId)
                .orElseGet(() -> roleRepository.findByNameAndDeletedAtIsNull("ADMIN")
                        .orElseThrow(() -> new IllegalStateException(
                                "Rol ADMIN_EMPRESA no encontrado para la empresa " + companyId
                              + " (auto-provision fallo)")));

        String encoded = passwordEncoder.encode(req.getAdminPassword());
        User admin = User.builder()
                .name(req.getAdminFirstName())
                .lastname(req.getAdminLastName())
                .email(req.getAdminEmail())
                .username(req.getAdminUsername())
                .password(encoded)
                .roles(Set.of(adminRole))
                .status(Status.ACTIVE)
                .companyId(companyId)
                .platformRole(null)
                .build();
        User savedAdmin = userRepository.save(admin);
        // PA-RF-01: registrar la primera contrasena en el historial.
        passwordPolicyService.record(savedAdmin.getId(), encoded);
        log.info("HU-PLAT-02: admin inicial creado para empresa id={}: username={} con rol={}",
                companyId, savedAdmin.getUsername(), adminRole.getName());
        TenantContext.runAs(companyId, false, () ->
                auditPublisher.publishCreate(AuditModule.PA, "User", savedAdmin.getId(),
                        "Usuario " + adminRole.getName() + " inicial creado para empresa: "
                                + savedAdmin.getUsername() + " (" + savedAdmin.getEmail() + ")"));
        return savedAdmin.getId();
    }

    /** TX3 del saga PLAT-01: marca la empresa ACTIVE tras aprovisionamiento exitoso. */
    @Transactional
    public void markCompanyActive(Long companyId) {
        Company c = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        c.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(c);
        auditCompany(companyId, "UPDATE",
                "Empresa activada tras aprovisionamiento exitoso (PROVISIONING -> ACTIVE)");
    }

    /** PA-RF-PLAT-01 punto 4: marca la empresa en ERROR si el aprovisionamiento falla. */
    @Transactional
    public void markCompanyError(Long companyId, String reason) {
        try {
            Company c = companyRepository.findById(companyId).orElse(null);
            if (c != null) {
                c.setStatus(CompanyStatus.ERROR);
                companyRepository.save(c);
                auditCompany(companyId, "UPDATE",
                        "Aprovisionamiento fallido (PROVISIONING -> ERROR): " + reason);
            }
        } catch (Exception e) {
            log.error("No se pudo marcar ERROR la empresa {}: {}", companyId, e.getMessage());
        }
    }

    /**
     * PA-RF-PLAT-01 punto 5 (v3.0, Control de Cambios PA): los datos legales de la
     * empresa son obligatorios al crear empresa + primer administrador. Antes solo
     * eran razon social, NIT y DV; ahora tambien direccion principal, correo
     * corporativo y telefono principal.
     */
    private void validateCompanyLegalData(CreateCompanyRequest request) {
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            throw new IllegalArgumentException("La direccion principal de la empresa es obligatoria");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("El correo corporativo de la empresa es obligatorio");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("El telefono principal de la empresa es obligatorio");
        }
    }

    /** Validacion de NIT + DV reutilizable (HU-PA-10 E2 + HU-PA-PLAT-01 E4). */
    private void validateNitForCreate(CreateCompanyRequest request) {
        if (companyRepository.existsByNitAndDeletedAtIsNull(request.getNit())) {
            throw new IllegalArgumentException("Ya existe una empresa con el NIT " + request.getNit());
        }
        if (companyRepository.existsByNit(request.getNit())) {
            throw new IllegalArgumentException(
                    "El NIT " + request.getNit() + " corresponde a una empresa eliminada. "
                  + "Contacte al administrador de plataforma para reactivarla en lugar de crear una nueva.");
        }
        if (request.getDv() != null && !request.getDv().isBlank()) {
            String calculated = computeColombianDv(request.getNit());
            if (calculated != null && !calculated.equals(request.getDv().trim())) {
                throw new IllegalArgumentException(
                        "El digito de verificacion (DV=" + request.getDv() + ") no coincide con el "
                      + "calculado para NIT " + request.getNit() + " segun algoritmo DIAN. "
                      + "DV correcto: " + calculated);
            }
        }
    }

    /** HU-PA-RF-63: desactivar empresa (usuarios no podran loguearse). Wrapper legacy. */
    @Transactional
    public CompanyDTO deactivate(Long id) {
        return deactivate(id, "(legacy: sin motivo, retro-compat)", true, null);
    }

    /** Wrapper sin IP (retro-compat). */
    @Transactional
    public CompanyDTO deactivate(Long id, String reason, boolean force) {
        return deactivate(id, reason, force, null);
    }

    /**
     * PA-RF-PLAT-03 v3.0 (Control de Cambios PA, 2026-05-29): desactivacion
     * endurecida:
     *  - punto 4: no permite desactivar la ULTIMA empresa ACTIVE.
     *  - punto 5: no permite desactivar empresas en PROVISIONING/ERROR.
     *  - punto 3: registra la IP del PLATFORM_ADMIN ejecutor en auditoria.
     *  - punto 7: revoca las sesiones activas (refresh tokens) de los usuarios de
     *    la empresa y retorna la cantidad en {@code invalidatedSessions}.
     *  - jobs en ejecucion (force=false) -> IllegalStateException (409).
     */
    @Transactional
    public CompanyDTO deactivate(Long id, String reason, boolean force, String executorIp) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        if (c.getStatus() == CompanyStatus.INACTIVE) {
            log.info("Empresa ya estaba INACTIVE: id={}", id);
            CompanyDTO already = CompanyDTO.from(c);
            already.setInvalidatedSessions(0);
            return already;
        }

        // PA-RF-PLAT-03 punto 5: no desactivar empresas en estados transitorios.
        if (c.getStatus() == CompanyStatus.PROVISIONING || c.getStatus() == CompanyStatus.ERROR) {
            throw new IllegalArgumentException(
                "No se puede desactivar una empresa en estado " + c.getStatus()
              + ". Espere a que finalice el aprovisionamiento, repare con re-provision o eliminela.");
        }

        // PA-RF-PLAT-03 punto 4: no desactivar la unica empresa ACTIVE de la plataforma.
        long otherActive = companyRepository.findByStatusAndDeletedAtIsNull(CompanyStatus.ACTIVE)
                .stream().filter(x -> !x.getId().equals(id)).count();
        if (otherActive == 0) {
            throw new IllegalArgumentException(
                "No se puede desactivar la unica empresa ACTIVE de la plataforma. "
              + "Debe quedar al menos una empresa activa.");
        }

        CompanyStatus previousStatus = c.getStatus();

        // QA Bloque PA Bug 61 (HU-PA-PLAT-03 E4): chequear jobs en ejecucion.
        if (!force) {
            java.util.List<String> blocking = checkRunningJobsFor(id);
            if (!blocking.isEmpty()) {
                throw new IllegalStateException(
                    "Hay procesos en ejecucion para esta empresa: " + blocking
                  + ". Espere a que finalicen o reintente con force=true para forzar la desactivacion.");
            }
        }

        Number affectedNum = (Number) jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE company_id=? AND deleted_at IS NULL",
            Number.class, id);
        long affectedCount = affectedNum == null ? 0L : affectedNum.longValue();

        c.setStatus(CompanyStatus.INACTIVE);
        companyRepository.save(c);

        // PA-RF-PLAT-03 punto 7: revocar sesiones activas (refresh tokens) de los
        // usuarios de la empresa y contar cuantas se invalidaron.
        int invalidatedSessions = 0;
        try {
            java.util.List<Long> userIds = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE company_id=? AND deleted_at IS NULL", Long.class, id);
            invalidatedSessions = sessionService.revokeAllForUsers(userIds);
        } catch (Exception ex) {
            log.warn("revokeAllForUsers fallo para companyId={}: {}", id, ex.getMessage());
        }

        log.info("Empresa desactivada: id={} previousStatus={} affectedUsers={} invalidatedSessions={} reason={}",
                id, previousStatus, affectedCount, invalidatedSessions, reason);

        // QA Bloque PA Bug 59 (HU-PA-PLAT-03 E1): cutoff de JWT (access tokens) a nivel empresa.
        try {
            self().setJwtInvalidationCutoff(id);
        } catch (Exception ex) {
            log.debug("Set JWT_INVALIDATION_CUTOFF fallo para companyId={}: {}", id, ex.getMessage());
        }

        // PA-RF-PLAT-03 puntos 3/7: audit enriquecido con IP del ejecutor + invalidatedSessions.
        auditCompany(id, "UPDATE",
                "Empresa desactivada: " + c.getBusinessName()
                + " | previousStatus=" + previousStatus
                + " | affectedUsersCount=" + affectedCount
                + " | invalidatedSessions=" + invalidatedSessions
                + " | executorIp=" + executorIp
                + " | reason=" + reason);

        CompanyDTO dto = CompanyDTO.from(c);
        dto.setInvalidatedSessions(invalidatedSessions);
        return dto;
    }

    /**
     * QA Bloque PA Bug 61 (HU-PA-PLAT-03 E4, 2026-05-09): detecta procesos en
     * ejecucion para una empresa que bloquearian la desactivacion.
     */
    private java.util.List<String> checkRunningJobsFor(Long companyId) {
        java.util.List<String> blocking = new java.util.ArrayList<>();
        try {
            // Cierre mensual en proceso (cg_closing_entries en estado IN_PROGRESS)
            try {
                Number closing = (Number) jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM cg_closing_entries WHERE company_id=? AND status='IN_PROGRESS'",
                    Number.class, companyId);
                if (closing != null && closing.longValue() > 0) {
                    blocking.add("cierre contable en proceso (" + closing + ")");
                }
            } catch (Exception ignored) {}
            // Lotes AAEF en RECEIVED/PROCESSING (procesamiento async pendiente)
            try {
                Number aaef = (Number) jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM integration_batches WHERE company_id=? AND status IN ('RECEIVED','PROCESSING') AND deleted_at IS NULL",
                    Number.class, companyId);
                if (aaef != null && aaef.longValue() > 0) {
                    blocking.add("lotes AAEF en procesamiento (" + aaef + ")");
                }
            } catch (Exception ignored) {}
        } catch (Exception ex) {
            log.warn("checkRunningJobsFor fallo para companyId={}: {}", companyId, ex.getMessage());
        }
        return blocking;
    }

    /** Wrapper legacy sin motivo/IP. */
    @Transactional
    public CompanyDTO activate(Long id) {
        return activate(id, "(legacy: sin motivo)", null);
    }

    /**
     * PA-RF-PLAT-03 v3.0 (Control de Cambios PA, 2026-05-29): reactiva una empresa.
     * Ahora exige motivo (punto 1) y registra la IP del ejecutor (punto 3).
     */
    @Transactional
    public CompanyDTO activate(Long id, String reason, String executorIp) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        CompanyStatus previousStatus = c.getStatus();
        c.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(c);
        log.info("Empresa re-activada: id={} previousStatus={} reason={}", id, previousStatus, reason);
        auditCompany(id, "UPDATE",
                "Empresa re-activada: " + c.getBusinessName()
                + " | previousStatus=" + previousStatus
                + " | executorIp=" + executorIp
                + " | reason=" + reason);
        return CompanyDTO.from(c);
    }

    /**
     * QA Bloque PA Bug 59 (HU-PA-PLAT-03 E1): persiste el cutoff de invalidacion
     * de JWT en TX separada. Si falla por constraints u otros, no afecta la TX
     * principal del deactivate.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void setJwtInvalidationCutoff(Long companyId) {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE parameters SET value=?, updated_at=NOW() WHERE name=? AND company_id=?",
                String.valueOf(System.currentTimeMillis() / 1000), "JWT_INVALIDATION_CUTOFF", companyId);
            if (updated == 0) {
                jdbcTemplate.update(
                    "INSERT INTO parameters (name, value, category, status, company_id, created_at, updated_at) "
                  + "VALUES (?, ?, 'COMPANY', 'ACTIVE', ?, NOW(), NOW())",
                    "JWT_INVALIDATION_CUTOFF", String.valueOf(System.currentTimeMillis() / 1000), companyId);
            }
        } catch (Exception ex) {
            log.debug("setJwtInvalidationCutoff: {}", ex.getMessage());
        }
    }

    /**
     * Self-reference para invocar metodos @Transactional propios (REQUIRES_NEW)
     * sin caer en self-invocation que bypasea el proxy de Spring.
     */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private CompanyService self;
    private CompanyService self() { return self; }

    /**
     * QA Bloque PA Bug 58 (HU-PA-PLAT-01 E4, 2026-05-09): algoritmo DIAN para
     * calcular el digito de verificacion (DV) de un NIT colombiano.
     *
     * <p>Algoritmo: se asignan factores [3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47,
     * 53, 59, 67, 71] al NIT de derecha a izquierda, se suma el producto, se
     * obtiene el modulo 11 y:
     *   - si modulo es 0 o 1 -> DV = modulo
     *   - si modulo es >=2 -> DV = 11 - modulo
     *
     * @param nit cadena numerica (cualquier longitud razonable)
     * @return digito de verificacion como String "0".."9", o null si nit invalido
     */
    static String computeColombianDv(String nit) {
        // Fuente unica de verdad: com.sigcon.backend.utils.DianVerificationDigit.
        return com.sigcon.backend.utils.DianVerificationDigit.compute(nit);
    }
}
