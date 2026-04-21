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

    /** HU-PLAT-01 E1: listado paginado de empresas (incluye INACTIVE). */
    @Transactional(readOnly = true)
    public Page<CompanyDTO> findAll(Pageable pageable) {
        return companyRepository.findAll(pageable).map(CompanyDTO::from);
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
        return CompanyDTO.from(c);
    }

    /**
     * HU-PLAT-01 E3: crea una empresa vacia (sin admin). Valida NIT unico y
     * dispara auto-provision de periodos/mapeos/cost-center.
     *
     * @throws IllegalArgumentException si el NIT ya esta registrado en otra empresa activa
     */
    @Transactional
    public CompanyDTO create(CreateCompanyRequest request) {
        // HU-PA-RF-60 E2: NIT unico entre empresas activas
        if (companyRepository.existsByNitAndDeletedAtIsNull(request.getNit())) {
            throw new IllegalArgumentException(
                    "Ya existe una empresa con el NIT " + request.getNit());
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
        return CompanyDTO.from(c);
    }

    /**
     * HU-PLAT-02: crea empresa + primer usuario ADMIN de esa empresa atomicamente.
     * Valida NIT unico + email/username unicos. Si algo falla, rollback total.
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

        Role adminRole = roleRepository.findByNameAndDeletedAtIsNull("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "Rol ADMIN no encontrado en la BD. Ejecute V9-J primero."));

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
        userRepository.save(admin);
        log.info("HU-PLAT-02: admin inicial creado para empresa id={}: username={}",
                created.getId(), admin.getUsername());
        return created;
    }

    /** HU-PA-RF-63: desactivar empresa (usuarios no podran loguearse). */
    @Transactional
    public CompanyDTO deactivate(Long id) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        c.setStatus(CompanyStatus.INACTIVE);
        companyRepository.save(c);
        log.info("Empresa desactivada: id={}", id);
        return CompanyDTO.from(c);
    }

    @Transactional
    public CompanyDTO activate(Long id) {
        Company c = companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
        c.setStatus(CompanyStatus.ACTIVE);
        companyRepository.save(c);
        log.info("Empresa re-activada: id={}", id);
        return CompanyDTO.from(c);
    }
}
