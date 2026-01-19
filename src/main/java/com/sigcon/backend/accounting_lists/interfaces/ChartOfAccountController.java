package com.sigcon.backend.accounting_lists.interfaces;

import com.sigcon.backend.accounting_lists.application.ChartOfAccountDTO;
import com.sigcon.backend.accounting_lists.domain.service.ChartOfAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chartOfAccounts")
@RequiredArgsConstructor
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

    @PostMapping
    @PreAuthorize( "hasAuthority('PERM_CREATE_CHART_OF_ACCOUNT')")
    public ResponseEntity<?> createChartOfAccount(@Valid @RequestBody ChartOfAccountDTO request) {

        try {
            chartOfAccountService.createChartOfAccount(request);
            return ResponseEntity.ok("La cuenta ha sido creada exitosamente en el catálogo PUC");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al guardar la información, intente nuevamente");
        }
    }

}
