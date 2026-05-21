package com.sigcon.backend.banks.trm.interfaces.controller;

import com.sigcon.backend.banks.trm.domain.model.ConfigTrm;
import com.sigcon.backend.banks.trm.domain.model.TrmHistorica;
import com.sigcon.backend.banks.trm.domain.service.TrmService;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.utils.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * BNK-HU-076 E1/E8: gestión de la TRM (carga manual, histórico, monedas soportadas,
 * política por empresa) y disparo manual del carry-forward (stand-in del fetch oficial).
 */
@RestController
@RequestMapping("/api/v1/banks/trm")
@RequiredArgsConstructor
@Tag(name = "BNK - TRM y moneda extranjera (HU-076)",
     description = "Tasa Representativa del Mercado: carga, histórico, política y conversión")
public class TrmController {

    private final TrmService service;
    private final UserUtil userUtil;
    private static final String VER = "hasAuthority('PERM_BNK.CUENTAS.VER') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";
    private static final String EDITAR = "hasAuthority('PERM_BNK.CUENTAS.EDITAR') or hasAnyAuthority('ROLE_ADMIN_EMPRESA','PLATFORM_ADMIN')";

    @Operation(summary = "Registrar/actualizar TRM manual de una moneda en una fecha (BNK-HU-076 E1)")
    @PreAuthorize(EDITAR)
    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody RegistrarTrmRequest req) {
        User u = userUtil.getUser();
        TrmHistorica trm = service.registrarTrm(req.getCurrencyIso(), req.getFecha(), req.getValorCop(),
                req.getFuente(), u != null ? u.getId() : null);
        return ResponseEntity.ok(Map.of("id", trm.getId(), "moneda", trm.getCurrencyIso(),
                "fecha", trm.getFecha(), "valorCop", trm.getValorCop(), "fuente", trm.getFuente()));
    }

    @Operation(summary = "Histórico de TRM de una moneda (BNK-HU-076 E1)")
    @PreAuthorize(VER)
    @GetMapping("/historica")
    public ResponseEntity<?> historica(@RequestParam String currencyIso,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(service.historica(currencyIso, desde, hasta));
    }

    @Operation(summary = "TRM vigente para una fecha (BNK-HU-076 E2/E5)")
    @PreAuthorize(VER)
    @GetMapping("/vigente")
    public ResponseEntity<?> vigente(@RequestParam String currencyIso,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        BigDecimal trm = service.trmParaFecha(currencyIso, fecha);
        return ResponseEntity.ok(java.util.Collections.singletonMap("trm", trm));
    }

    @Operation(summary = "Monedas soportadas (distintas de COP) (BNK-HU-076 E8)")
    @PreAuthorize(VER)
    @GetMapping("/monedas-soportadas")
    public ResponseEntity<?> monedas() {
        return ResponseEntity.ok(service.monedasSoportadas());
    }

    @Operation(summary = "Consultar política de TRM de la empresa (BNK-HU-076 E8)")
    @PreAuthorize(VER)
    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        ConfigTrm cfg = service.getOrCreateConfig();
        return ResponseEntity.ok(Map.of("politicaTrm", cfg.getPoliticaTrm()));
    }

    @Operation(summary = "Actualizar política de TRM de la empresa (BNK-HU-076 E8)")
    @PreAuthorize(EDITAR)
    @PutMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody ConfigTrmRequest req) {
        ConfigTrm cfg = service.updateConfig(req.getPoliticaTrm());
        return ResponseEntity.ok(Map.of("politicaTrm", cfg.getPoliticaTrm()));
    }

    @Operation(summary = "Disparar manualmente el carry-forward de TRM (stand-in del fetch oficial, BNK-HU-076 E1)")
    @PreAuthorize(EDITAR)
    @PostMapping("/carry-forward")
    public ResponseEntity<?> carryForward(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(service.carryForwardForCurrentTenant(fecha != null ? fecha : LocalDate.now()));
    }

    @Data
    public static class RegistrarTrmRequest {
        private String currencyIso;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate fecha;
        private BigDecimal valorCop;
        private String fuente; // MANUAL | OFICIAL | ULTIMA_PUBLICADA
    }

    @Data
    public static class ConfigTrmRequest {
        private String politicaTrm; // FECHA_MOVIMIENTO | FECHA_CIERRE
    }
}
