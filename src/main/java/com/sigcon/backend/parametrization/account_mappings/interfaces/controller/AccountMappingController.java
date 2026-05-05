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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')")
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
}
