package com.sigcon.backend.platform.companies.interfaces.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.platform.companies.application.CompanyDTO;
import com.sigcon.backend.platform.companies.application.CreateCompanyRequest;
import com.sigcon.backend.platform.companies.application.CreateCompanyWithAdminRequest;
import com.sigcon.backend.platform.companies.application.UpdateCompanyRequest;
import com.sigcon.backend.platform.companies.domain.service.CompanyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * CRUD de empresas (tenants) para {@code PLATFORM_ADMIN}.
 *
 * <p>Todos los endpoints requieren el authority {@code PLATFORM_ADMIN}, que el
 * {@code JwtService} inyecta al JWT cuando el usuario tiene
 * {@code platform_role='PLATFORM_ADMIN'} en la tabla {@code users} (ver V10-A).
 * Un admin de empresa ({@code ROLE_ADMIN}) NO puede acceder a estos endpoints
 * — recibe HTTP 403.
 *
 * <p><b>HUs cubiertas</b>
 * <ul>
 *   <li>HU-PLAT-01: CRUD basico de empresas (list / get / update).</li>
 *   <li>HU-PLAT-02: Alta atomica empresa + primer admin ({@code POST /with-admin}).</li>
 *   <li>HU-PLAT-05: Desactivar / reactivar empresas.</li>
 * </ul>
 *
 * <p><b>Side effects al crear empresa</b>: {@code CompanyService.create} invoca
 * {@code _tenant_auto_provision(companyId, year)} (funcion PL/pgSQL definida en
 * V10-D) que siembra automaticamente 12 periodos contables, 18 mapeos PUC default
 * y 1 centro de costo para el tenant nuevo.
 *
 * <p>Ver tambien: {@code ModuleService.getModulesMenu} filtra el modulo
 * "Plataforma" de modo que solo PLATFORM_ADMIN lo ve en su dashboard.
 */
@RestController
@RequestMapping("/api/platform/companies")
@RequiredArgsConstructor
@Tag(name = "Platform - Companies (CRUD)",
     description = "Gestion cross-empresa de tenants. Solo PLATFORM_ADMIN.")
@PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
public class CompanyController {

    private final CompanyService companyService;

    /**
     * Listado paginado de empresas (incluye INACTIVE). Solo visible a PLATFORM_ADMIN.
     * Cubre HU-PLAT-01.
     *
     * @param page 0-based, default 0
     * @param size 1-100 rows per page, default 20
     */
    @GetMapping
    @Operation(summary = "HU-PLAT-01 E1: listar empresas paginadas",
               description = "Retorna Page<CompanyDTO> con todas las empresas (incluyendo INACTIVE). "
                           + "Pensado para el dashboard de PLATFORM_ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado paginado entregado",
                     content = @Content(schema = @Schema(implementation = CompanyDTO.class))),
        @ApiResponse(responseCode = "403",
                     description = "Usuario no es PLATFORM_ADMIN (admin de empresa recibe este codigo)")
    })
    public ResponseEntity<Page<CompanyDTO>> list(
            @Parameter(description = "Indice de pagina 0-based", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Filas por pagina (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(companyService.findAll(pageable));
    }

    /**
     * Obtiene el detalle de una empresa por id. Cubre HU-PLAT-01 E2.
     */
    @GetMapping("/{id}")
    @Operation(summary = "HU-PLAT-01 E2: obtener empresa por id",
               description = "Retorna la representacion completa de la empresa (detalle del admin dashboard).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Empresa encontrada"),
        @ApiResponse(responseCode = "400", description = "ID no existe o esta eliminada"),
        @ApiResponse(responseCode = "403", description = "Usuario no es PLATFORM_ADMIN")
    })
    public ResponseEntity<CompanyDTO> findOne(
            @Parameter(description = "ID interno de la empresa", example = "2")
            @PathVariable Long id) {
        return ResponseEntity.ok(companyService.findById(id));
    }

    /**
     * Crea una empresa vacia sin admin inicial. Prefiera {@link #createWithAdmin}
     * para onboarding productivo. Cubre HU-PLAT-01 E3.
     *
     * <p>Side effect: auto-provision de 12 periodos + 18 mapeos PUC + 1 CC default
     * para el nuevo tenant (via {@code _tenant_auto_provision} PL/pgSQL).
     */
    @PostMapping
    @Operation(summary = "HU-PLAT-01 E3: crear empresa (sin admin)",
               description = "Valida unicidad de NIT entre empresas activas. Tras crear, ejecuta "
                           + "_tenant_auto_provision para sembrar periodos, mapeos y cost_center default.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Empresa creada con auto-provision aplicado"),
        @ApiResponse(responseCode = "400",
                     description = "Validacion fallida (NIT duplicado, razon social vacia, NIT no numerico, etc.)"),
        @ApiResponse(responseCode = "403", description = "Usuario no es PLATFORM_ADMIN")
    })
    public ResponseEntity<CompanyDTO> create(@Valid @RequestBody CreateCompanyRequest request) {
        CompanyDTO dto = companyService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * HU-PLAT-02: flow atomico que el PLATFORM_ADMIN usa en el onboarding de clientes.
     * Crea {@link com.sigcon.backend.platform.companies.domain.model.Company} +
     * {@link com.sigcon.backend.parametrization.users.domain.model.User}(role=ADMIN,
     * company_id=nueva) en UNA transaccion. Si cualquiera de los dos falla,
     * rollback total (sin empresa huerfana).
     *
     * <p>Validaciones: NIT unico entre empresas activas, email del admin unico,
     * username del admin unico.
     */
    @PostMapping("/with-admin")
    @Operation(summary = "HU-PLAT-02: crear empresa + primer admin atomicamente",
               description = "Transaccion unica que crea la Company y el User admin de esa empresa. "
                           + "Si cualquiera falla, rollback de ambos. Tras el commit, la empresa queda "
                           + "auto-provisionada (12 periodos + 18 mapeos + 1 cost_center).")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Empresa + admin creados correctamente",
                     content = @Content(schema = @Schema(implementation = CompanyDTO.class))),
        @ApiResponse(responseCode = "400",
                     description = "NIT / email / username duplicado, password menor a 6 chars, etc."),
        @ApiResponse(responseCode = "403", description = "Usuario no es PLATFORM_ADMIN")
    })
    public ResponseEntity<CompanyDTO> createWithAdmin(
            @Valid @RequestBody CreateCompanyWithAdminRequest request) {
        CompanyDTO dto = companyService.createWithAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Actualiza campos editables de una empresa. Cubre HU-PLAT-01 E4.
     *
     * <p>Nota: el cambio de NIT NO se permite si la empresa ya tiene movimientos
     * contables registrados (riesgo fiscal) — validacion pendiente en el service.
     */
    @PutMapping("/{id}")
    @Operation(summary = "HU-PLAT-01 E4: actualizar empresa",
               description = "Valida que el nuevo NIT no colisione con otra empresa activa.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Empresa actualizada"),
        @ApiResponse(responseCode = "400", description = "Validacion fallida (NIT duplicado, etc.)"),
        @ApiResponse(responseCode = "403", description = "Usuario no es PLATFORM_ADMIN")
    })
    public ResponseEntity<CompanyDTO> update(
            @Parameter(description = "ID de la empresa a actualizar", example = "2")
            @PathVariable Long id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        return ResponseEntity.ok(companyService.update(id, request));
    }

    /**
     * HU-PLAT-05 E1: desactiva la empresa (soft, status=INACTIVE). Los usuarios
     * de esta empresa NO podran hacer login mientras el estado sea INACTIVE
     * (el gatekeeper de login valida en {@link com.sigcon.backend.parametrization.users.domain.service.AuthService}).
     *
     * <p>No elimina datos contables — es reversible con {@link #activate}.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "HU-PLAT-05 E1: desactivar empresa",
               description = "Soft disable: status=INACTIVE. Los usuarios tenant de esta empresa "
                           + "no podran loguearse hasta que se reactive. No borra datos.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Empresa desactivada"),
        @ApiResponse(responseCode = "400", description = "Empresa no existe"),
        @ApiResponse(responseCode = "403", description = "Usuario no es PLATFORM_ADMIN")
    })
    public ResponseEntity<CompanyDTO> deactivate(
            @Parameter(description = "ID de la empresa", example = "2")
            @PathVariable Long id) {
        return ResponseEntity.ok(companyService.deactivate(id));
    }

    /** HU-PLAT-05 E2: reactiva una empresa INACTIVE previamente. */
    @PostMapping("/{id}/activate")
    @Operation(summary = "HU-PLAT-05 E2: reactivar empresa",
               description = "Vuelve el status a ACTIVE. Los usuarios tenant pueden loguearse de nuevo.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Empresa reactivada"),
        @ApiResponse(responseCode = "400", description = "Empresa no existe"),
        @ApiResponse(responseCode = "403", description = "Usuario no es PLATFORM_ADMIN")
    })
    public ResponseEntity<CompanyDTO> activate(
            @Parameter(description = "ID de la empresa", example = "2")
            @PathVariable Long id) {
        return ResponseEntity.ok(companyService.activate(id));
    }
}
