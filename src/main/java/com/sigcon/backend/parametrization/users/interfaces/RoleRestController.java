package com.sigcon.backend.parametrization.users.interfaces;

import com.sigcon.backend.parametrization.users.application.role.RoleRequest;
import com.sigcon.backend.parametrization.users.domain.service.RoleService;
import com.sigcon.backend.utils.DataTableRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PA-RF-03 / PA-RF-04 v3.0 (Control de Cambios PA, 2026-05-29): endpoints REST
 * de roles bajo {@code /api/roles}, alineados con la nomenclatura del documento
 * de control de cambios.
 *
 * <p>Conviven con los endpoints legacy {@code /roles/*} (RoleController) que el
 * frontend actual sigue usando; estos nuevos NO rompen los anteriores.
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@Tag(name = "1. Parametrizacion - Roles (REST v3.0)",
     description = "Endpoints REST de roles + catalogo de permisos (PA-RF-03/04 v3.0)")
public class RoleRestController {

    private final RoleService roleService;

    /**
     * PA-RF-03 v3.0: listado de roles (GET /api/roles).
     *
     * <p>Dos modos sobre el mismo endpoint:
     * <ul>
     *   <li><b>DataTables</b> (cuando llega el parametro {@code draw}): el frontend de
     *       administracion de roles consume este endpoint. Reconstruimos un
     *       {@link DataTableRequest} desde los query params (paginacion + busqueda
     *       global + filtros de columna name/status/type) y delegamos en la logica
     *       legacy {@code getRoles}, reutilizando el mismo filtrado, paginacion,
     *       aislamiento multi-tenant, exclusion de soft-delete y diferenciacion de
     *       tipo. Devuelve el contrato DataTable ({@code draw/recordsTotal/recordsFiltered/data}).</li>
     *   <li><b>REST</b> (sin {@code draw}): respuesta paginada simple con metadatos
     *       (page/size/totalElements/totalPages) para consumidores de API.</li>
     * </ul>
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ROLES','PERM_PAR.ROLES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "PA-RF-03: listar roles (GET /api/roles; soporta DataTables y REST)")
    public ResponseEntity<?> list(@RequestParam Map<String, String> params) {
        // Modo DataTables: el frontend siempre envia 'draw'.
        if (params.containsKey("draw")) {
            return roleService.getRoles(buildDataTableRequest(params));
        }
        // Modo REST paginado (consumidores de API).
        int page = parseIntOrDefault(params.get("page"), 0);
        int size = parseIntOrDefault(params.get("size"), 20);
        String search = params.get("search");
        String sort = params.getOrDefault("sort", "id");
        String direction = params.getOrDefault("direction", "asc");
        Page<RoleRequest> data = roleService.listRolesPaged(page, size, search, sort, direction);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "content", data.getContent(),
                "page", data.getNumber(),
                "size", data.getSize(),
                "totalElements", data.getTotalElements(),
                "totalPages", data.getTotalPages()));
    }

    /**
     * Reconstruye un {@link DataTableRequest} desde los query params planos que
     * envia el DataTable del frontend en modo GET. Solo se rearman las columnas
     * filtrables del listado de roles (name/status/type) + busqueda global.
     */
    private DataTableRequest buildDataTableRequest(Map<String, String> p) {
        DataTableRequest req = new DataTableRequest();
        req.setDraw(parseIntOrDefault(p.get("draw"), 1));
        req.setStart(parseIntOrDefault(p.get("start"), 0));
        req.setLength(parseIntOrDefault(p.get("length"), 10));

        DataTableRequest.DataTableSearch global = new DataTableRequest.DataTableSearch();
        global.setValue(p.getOrDefault("search", ""));
        global.setRegex("true".equalsIgnoreCase(p.get("searchRegex")));
        req.setSearch(global);

        List<DataTableRequest.DataTableColumn> cols = new ArrayList<>();
        for (String colName : new String[]{"name", "status", "type"}) {
            DataTableRequest.DataTableColumn c = new DataTableRequest.DataTableColumn();
            c.setData(colName);
            c.setName(colName);
            c.setSearchable(true);
            c.setOrderable(false);
            DataTableRequest.DataTableSearch cs = new DataTableRequest.DataTableSearch();
            cs.setValue(p.getOrDefault("col_" + colName, ""));
            cs.setRegex("true".equalsIgnoreCase(p.get("colrx_" + colName)));
            c.setSearch(cs);
            cols.add(c);
        }
        req.setColumns(cols);
        return req;
    }

    private int parseIntOrDefault(String v, int def) {
        try {
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** PA-RF-04 v3.0: crear rol (POST /api/roles) -> 201 + catalogVersion. */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_CREATE_ROLE','PERM_PAR.ROLES.CREAR','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "PA-RF-04: crear rol (REST, ACID, retorna catalogVersion)")
    public ResponseEntity<?> create(@RequestBody RoleRequest request) {
        try {
            Long roleId = roleService.createRoleReturningId(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Rol creado exitosamente",
                    "roleId", roleId,
                    "catalogVersion", roleService.currentCatalogVersion()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "code", 400, "message", ex.getMessage()));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException tie) {
            throw tie;
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false, "code", 500,
                    "message", "Error al crear el rol: " + ex.getMessage()));
        }
    }

    /** PA-RF-04 v3.0: arbol de permisos (GET /api/roles/permissions/catalog) + catalogVersion. */
    @GetMapping("/permissions/catalog")
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_PERMISSIONS','PERM_VIEW_ROLES','PERM_PAR.ROLES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "PA-RF-04: catalogo/arbol de permisos para el selector de roles")
    public ResponseEntity<?> permissionsCatalog() {
        return ResponseEntity.ok(roleService.getPermissionCatalog());
    }
}
