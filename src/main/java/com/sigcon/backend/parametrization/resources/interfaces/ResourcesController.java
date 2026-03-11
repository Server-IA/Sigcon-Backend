package com.sigcon.backend.parametrization.resources.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.parametrization.resources.domain.service.ResourceService;
import com.sigcon.backend.utils.DataTableRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
@Tag(name = "Recursos", description = "Recursos del sistema")

public class ResourcesController {

    private final ResourceService resourceService;

    @PostMapping("/countries")
    @PreAuthorize("hasAuthority('PERM_VIEW_COUNTRY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Obtener países", description = "Obtener países del sistema <br> Permiso requerido: VIEW_COUNTRY")
    public ResponseEntity<?> getCountries(@RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getCountries(dtRequest);
    }

    @PostMapping("/municipalities")
    @PreAuthorize("hasAuthority('PERM_VIEW_MUNICIPALITY') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Obtener municipios", description = "Obtener municipios del sistema <br> Permiso requerido: VIEW_MUNICIPALITY")
    public ResponseEntity<?> getMunicipalities(@RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getMunicipalities(dtRequest);
    }

    @PostMapping("/payment-terms")
    @PreAuthorize("hasAuthority('PERM_VIEW_PAYMENT_TERM') or hasAuthority('ROLE_SUPERADMIN')")
    @Operation(summary = "Obtener términos de pago", description = "Obtener términos de pago del sistema <br> Permiso requerido: VIEW_PAYMENT_TERM")
    public ResponseEntity<?> getPaymentTerms(@RequestBody(required = false) DataTableRequest dtRequest) {
        return resourceService.getAllPaymentTerms(dtRequest);
    }

}
