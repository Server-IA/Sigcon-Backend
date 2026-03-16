package com.sigcon.backend.assets.niif_alerts.interfaces;

import com.sigcon.backend.assets.niif_alerts.application.VerifyNiifRequest;
import com.sigcon.backend.assets.niif_alerts.application.NiifCorrectionRequest;
import com.sigcon.backend.assets.niif_alerts.domain.service.NiifComplianceService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets/niif")
@RequiredArgsConstructor
@Tag(name = "NIIF Compliance", description = "Endpoints for NIIF compliance verification and correction")
public class NiifComplianceController {

    private final NiifComplianceService service;

    /**
     * NIIF-RF-01: Verificación de cumplimiento NIIF
     * POST /api/v1/assets/niif/verify
     */
    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('PERM_VERIFY_NIIF') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> verify(@RequestBody VerifyNiifRequest request){

        return ResponseEntity.ok(service.verify(request));

    }

    /**
     * NIIF-RF-02: Aplicación de corrección NIIF
     * POST /api/v1/assets/niif/correct
     */
    @PostMapping("/correct")
    @PreAuthorize("hasAuthority('PERM_CORRECT_NIIF') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> correct(@RequestBody NiifCorrectionRequest request){

        return ResponseEntity.ok(service.applyCorrection(request));

    }

}