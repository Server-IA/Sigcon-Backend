package com.sigcon.backend.parametrization.account_mappings.interfaces.controller;

import com.sigcon.backend.parametrization.account_mappings.application.AccountMappingDTO;
import com.sigcon.backend.parametrization.account_mappings.domain.service.AccountMappingService;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST de solo lectura para consultar los mapeos concepto-cuenta contable.
 *
 * <p>Esta vista es de solo lectura intencionalmente: los mapeos se siembran desde la
 * migracion {@code V31__account_mappings.sql} con los valores estandar del PUC
 * colombiano. No se expone edicion en esta version; si un contador requiere cambiar
 * un mapeo, debe hacerse via DDL controlado por el equipo de desarrollo.
 *
 * <p>El endpoint se expone unicamente a rol ADMIN para auditoria y diagnostico.
 */
@RestController
@RequestMapping("/api/account-mappings")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "1. Módulo de Parametrización - Mapeo de Cuentas Contables",
    description = "Consulta de mapeos entre conceptos logicos (AR_CLIENTES, AP_PROVEEDORES, etc.) "
                + "y cuentas contables reales del PUC colombiano. Usados por los motores "
                + "de asientos contables automaticos (AR, AP, BNK, ACT)."
)
public class AccountMappingController {

    private final AccountMappingService accountMappingService;

    /**
     * Lista todos los mapeos concepto-cuenta configurados en el sistema.
     * Incluye el codigo PUC sugerido y el nombre personalizado de la cuenta real.
     *
     * @return lista de {@link AccountMappingDTO} ordenada por codigo de concepto
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ACCOUNT_MAPPING','TEMP_PERM_VIEW_ACCOUNT_MAPPING','TEMP_VIEW_ACCOUNT_MAPPING','PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PAR.MAPEOS_CONTABLES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(
        summary = "Listar todos los mapeos contables",
        description = "Retorna la configuracion completa de mapeos concepto-cuenta. "
                    + "Solo rol ADMIN. No modifica datos."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado retornado exitosamente"),
        @ApiResponse(responseCode = "403", description = "Sin permisos (requiere ROLE_ADMIN)"),
        @ApiResponse(responseCode = "500", description = "Error interno al consultar")
    })
    public ResponseEntity<?> listAll() {
        try {
            List<AccountMappingDTO> mappings = accountMappingService.listAll();
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                    Optional.of("Mapeos contables listados correctamente"),
                    Optional.of(mappings)));
        } catch (com.sigcon.backend.platform.tenant.TenantIsolationException __tie) {
            throw __tie;
        } catch (Exception e) {
            log.error("Error al listar mapeos contables", e);
            return ResponseEntity.internalServerError().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        }
    }

    /**
     * Resuelve el ID de cuenta contable para un concepto dado.
     * Util para diagnostico: verificar que un concepto tenga la cuenta esperada.
     *
     * @param conceptCode codigo del concepto (ej. AR_CLIENTES)
     * @return mapeo con ID de cuenta y metadatos
     */
    @GetMapping("/{conceptCode}")
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ACCOUNT_MAPPING','TEMP_PERM_VIEW_ACCOUNT_MAPPING','TEMP_VIEW_ACCOUNT_MAPPING','PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PAR.MAPEOS_CONTABLES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(
        summary = "Consultar un mapeo por codigo de concepto",
        description = "Retorna el mapeo especifico para un concepto. 404 si el concepto no esta mapeado."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapeo encontrado"),
        @ApiResponse(responseCode = "404", description = "Concepto no configurado"),
        @ApiResponse(responseCode = "403", description = "Sin permisos (requiere ROLE_ADMIN)")
    })
    public ResponseEntity<?> findByConcept(@PathVariable("conceptCode") String conceptCode) {
        return accountMappingService.listAll().stream()
                .filter(m -> m.getConceptCode().equalsIgnoreCase(conceptCode))
                .findFirst()
                .<ResponseEntity<?>>map(dto -> ResponseEntity.ok(
                        SuccessRespondJson.getSuccessRespondMessage(
                                Optional.of("Mapeo encontrado"), Optional.of(dto))))
                .orElseGet(() -> ResponseEntity.status(404).body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of("Concepto no configurado: " + conceptCode))));
    }

    /**
     * QA Bloque PA Bug 92 (HU-TENNAT-04 E2, 2026-05-11): actualiza la cuenta
     * destino de un concepto. Acepta PUT y PATCH para flexibilidad de cliente.
     *
     * <p>Body: {"accountingAccountId": 123}
     */
    @PatchMapping("/{conceptCode}")
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ACCOUNT_MAPPING','TEMP_PERM_VIEW_ACCOUNT_MAPPING','TEMP_VIEW_ACCOUNT_MAPPING','PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PAR.MAPEOS_CONTABLES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(
        summary = "Actualizar la cuenta destino de un mapeo (HU-TENNAT-04 E2)",
        description = "Permite al admin de empresa reasignar la cuenta contable destino de un "
                    + "concepto (ej. AR_CLIENTES) sin necesidad de DDL. La nueva cuenta debe "
                    + "existir y pertenecer a la misma empresa. Invalida el cache."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapeo actualizado"),
        @ApiResponse(responseCode = "400", description = "Concepto o cuenta destino invalido"),
        @ApiResponse(responseCode = "403", description = "Sin permisos")
    })
    public ResponseEntity<?> updateMapping(
            @PathVariable("conceptCode") String conceptCode,
            @RequestBody Map<String, Object> body) {
        try {
            Object idRaw = body.get("accountingAccountId");
            if (idRaw == null) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("accountingAccountId es obligatorio")));
            }
            Long accountId = idRaw instanceof Number n
                    ? n.longValue() : Long.parseLong(String.valueOf(idRaw).trim());
            AccountMappingDTO updated = accountMappingService.updateMapping(conceptCode, accountId);
            return ResponseEntity.ok(SuccessRespondJson.getSuccessRespondMessage(
                Optional.of("Mapeo actualizado correctamente"), Optional.of(updated)));
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(
                    Optional.of("accountingAccountId debe ser un numero")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage())));
        }
    }

    @PutMapping("/{conceptCode}")
    @PreAuthorize("hasAnyAuthority('PERM_VIEW_ACCOUNT_MAPPING','TEMP_PERM_VIEW_ACCOUNT_MAPPING','TEMP_VIEW_ACCOUNT_MAPPING','PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PERM_PAR.MAPEOS_CONTABLES.VER','TEMP_PAR.MAPEOS_CONTABLES.VER','ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN','ROLE_ADMIN')")
    @Operation(summary = "Alias PUT del PATCH /{conceptCode}")
    public ResponseEntity<?> updateMappingPut(
            @PathVariable("conceptCode") String conceptCode,
            @RequestBody Map<String, Object> body) {
        return updateMapping(conceptCode, body);
    }
}
