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
        // QA Bloque PA Bug 25 (HU-PA-10 E2, 2026-05-09): la unicidad del NIT debe
        // incluir empresas soft-deleted. En Colombia el NIT es identificador fiscal
        // y reusarlo aunque la empresa este "eliminada" abre riesgos contables. Si
        // hay un soft-deleted con el mismo NIT, mensaje claro al PLATFORM_ADMIN
        // para que reactive en lugar de duplicar.
        if (companyRepository.existsByNitAndDeletedAtIsNull(request.getNit())) {
            throw new IllegalArgumentException(
                    "Ya existe una empresa con el NIT " + request.getNit());
        }
        if (companyRepository.existsByNit(request.getNit())) {
            throw new IllegalArgumentException(
                    "El NIT " + request.getNit() + " corresponde a una empresa eliminada. "
                  + "Contacte al administrador de plataforma para reactivarla en lugar de crear una nueva.");
        }
        // QA Bloque PA Bug 58 (HU-PA-PLAT-01 E4, 2026-05-09): validar DV con
        // algoritmo DIAN (Resolucion 12717/1972). Si el cliente envia dv y NO
        // coincide con el calculado, rechazar.
        if (request.getDv() != null && !request.getDv().isBlank()) {
            String calculated = computeColombianDv(request.getNit());
            if (calculated != null && !calculated.equals(request.getDv().trim())) {
                throw new IllegalArgumentException(
                        "El digito de verificacion (DV=" + request.getDv() + ") no coincide con el "
                      + "calculado para NIT " + request.getNit() + " segun algoritmo DIAN. "
                      + "DV correcto: " + calculated);
            }
        }

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
     * HU-PLAT-02 / HU-PA-10: crea empresa + primer usuario ADMIN_EMPRESA de esa empresa
     * atomicamente. Valida NIT unico + email/username unicos. Si algo falla, rollback total.
     *
     * <p>QA Bloque PA Bug 24 (2026-05-09): el primer admin se asocia al rol
     * {@code ADMIN_EMPRESA} del tenant recien creado (no al rol global {@code ADMIN}).
     * Esto cumple HU-PA-10 E1 que exige aprovisionar los 6 roles predefinidos y
     * asignar el primer admin como ADMIN_EMPRESA del tenant.
     */
    @Transactional
    public CompanyDTO createWithAdmin(CreateCompanyWithAdminRequest req) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(req.getAdminEmail())) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario con el email " + req.getAdminEmail());
        }
        if (userRepository.findByUsernameOrEmail(req.getAdminUsername(), req.getAdminEmail()).isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un usuario con el username " + req.getAdminUsername());
        }

        CompanyDTO created = create(req.getCompany());

        // QA Bloque PA Bug 24 (HU-PA-10 E1): usar el rol ADMIN_EMPRESA del tenant
        // recien creado (clonado por _seed_predefined_roles_for_tenant). Si no se
        // resuelve, fallback al rol global ADMIN para no bloquear la transaccion.
        Role adminRole = roleRepository
                .findByNameIgnoreCaseAndCompanyIdAndDeletedAtIsNull("ADMIN_EMPRESA", created.getId())
                .orElseGet(() -> roleRepository.findByNameAndDeletedAtIsNull("ADMIN")
                        .orElseThrow(() -> new IllegalStateException(
                                "Rol ADMIN_EMPRESA no encontrado para la empresa "
                              + created.getId() + " (auto-provision fallo)")));

        User admin = User.builder()
                .name(req.getAdminFirstName())
                .lastname(req.getAdminLastName())
                .email(req.getAdminEmail())
                .username(req.getAdminUsername())
                .password(passwordEncoder.encode(req.getAdminPassword()))
                .roles(Set.of(adminRole))
                .status(Status.ACTIVE)
                .companyId(created.getId())
                .platformRole(null)
                .build();
        User savedAdmin = userRepository.save(admin);
        log.info("HU-PLAT-02: admin inicial creado para empresa id={}: username={} con rol={}",
                created.getId(), admin.getUsername(), adminRole.getName());
        // Auditar creacion del admin dentro del contexto de la nueva empresa.
        TenantContext.runAs(created.getId(), false, () ->
                auditPublisher.publishCreate(AuditModule.PA, "User", savedAdmin.getId(),
                        "Usuario " + adminRole.getName() + " inicial creado para empresa: "
                                + savedAdmin.getUsername() + " (" + savedAdmin.getEmail() + ")"));
        // QA Bloque PA Bug 56 (HU-PA-PLAT-01 E1, 2026-05-09): exponer el adminUserId
        // en el campo extra del DTO para que el frontend pueda navegar al user creado.
        // Como CompanyDTO no tenia el campo, lo agregamos como atributo @Transient en
        // la respuesta via wrapper Map en el controller. Aca solo guardamos el id en
        // un campo adicional del DTO devuelto.
        created.setAdminUserId(savedAdmin.getId());
        created.setAdminEmail(savedAdmin.getEmail());
        created.setAdminUsername(savedAdmin.getUsername());
        return created;
    }

    /** HU-PA-RF-63: desactivar empresa (usuarios no podran loguearse). Wrapper legacy. */
    @Transactional
    public CompanyDTO deactivate(Long id) {
        return deactivate(id, "(legacy: sin motivo, retro-compat)", true);
    }

    /**
     * QA Bloque PA Bug 59-62 (HU-PA-PLAT-03 E1+E3+E4+E5, 2026-05-09): version
     * enriquecida con:
     *  - E3: motivo obligatorio (validado en el controller con minimo 30 chars).
     *  - E4: chequea jobs activos antes de desactivar. Si hay y force=false, lanza
     *        IllegalStateException -> 409.
     *  - E5: audit log con previousStatus + reason + affectedUsersCount.
     *  - E1: mass-blacklist de tokens activos de los users de la empresa.
     */
    @Transactional
    public CompanyDTO deactivate(Long id, String reason, boolean force) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));

        if (c.getStatus() == CompanyStatus.INACTIVE) {
            log.info("Empresa ya estaba INACTIVE: id={}", id);
            return CompanyDTO.from(c);
        }

        CompanyStatus previousStatus = c.getStatus();

        // QA Bloque PA Bug 61 (HU-PA-PLAT-03 E4): chequear jobs en ejecucion
        if (!force) {
            java.util.List<String> blocking = checkRunningJobsFor(id);
            if (!blocking.isEmpty()) {
                throw new IllegalStateException(
                    "Hay procesos en ejecucion para esta empresa: " + blocking
                  + ". Espere a que finalicen o reintente con force=true para forzar la desactivacion.");
            }
        }

        // Affected users count (para audit)
        Number affectedNum = (Number) jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE company_id=? AND deleted_at IS NULL",
            Number.class, id);
        long affectedCount = affectedNum == null ? 0L : affectedNum.longValue();

        c.setStatus(CompanyStatus.INACTIVE);
        companyRepository.save(c);
        log.info("Empresa desactivada: id={} previousStatus={} affectedUsers={} reason={}",
                id, previousStatus, affectedCount, reason);

        // QA Bloque PA Bug 59 (HU-PA-PLAT-03 E1): blacklist tokens activos.
        // El cutoff se persiste en una TX SEPARADA via @Transactional(REQUIRES_NEW)
        // en setJwtInvalidationCutoff() para que un fallo aqui NO rompa el deactivate.
        try {
            self().setJwtInvalidationCutoff(id);
        } catch (Exception ex) {
            log.debug("Set JWT_INVALIDATION_CUTOFF fallo para companyId={}: {}", id, ex.getMessage());
        }

        // QA Bloque PA Bug 62 (HU-PA-PLAT-03 E5): audit enriquecido
        auditCompany(id, "UPDATE",
                "Empresa desactivada: " + c.getBusinessName()
                + " | previousStatus=" + previousStatus
                + " | affectedUsersCount=" + affectedCount
                + " | reason=" + reason);
        return CompanyDTO.from(c);
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

    @Transactional
    public CompanyDTO activate(Long id) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        c.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(c);
        log.info("Empresa re-activada: id={}", id);
        auditCompany(id, "UPDATE",
                "Empresa re-activada: " + c.getBusinessName());
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
        if (nit == null) return null;
        String n = nit.trim();
        if (n.isEmpty() || !n.chars().allMatch(Character::isDigit)) return null;
        int[] factors = {3, 7, 13, 17, 19, 23, 29, 37, 41, 43, 47, 53, 59, 67, 71};
        int sum = 0;
        for (int i = 0; i < n.length(); i++) {
            int digit = n.charAt(n.length() - 1 - i) - '0';
            int factor = (i < factors.length) ? factors[i] : 0;
            sum += digit * factor;
        }
        int mod = sum % 11;
        int dv = (mod >= 2) ? (11 - mod) : mod;
        return String.valueOf(dv);
    }
}
