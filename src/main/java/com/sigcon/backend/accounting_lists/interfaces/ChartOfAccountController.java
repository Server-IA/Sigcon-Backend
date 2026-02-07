package com.sigcon.backend.accounting_lists.interfaces;

import com.sigcon.backend.accounting_lists.application.ChartOfAccountDTO;
import com.sigcon.backend.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.accounting_lists.domain.model.ChartOfAccount;
import com.sigcon.backend.accounting_lists.domain.service.ChartOfAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/chartOfAccounts")
@RequiredArgsConstructor
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_CHART_OF_ACCOUNT')")
    public ResponseEntity<?> createChartOfAccount(@Valid @RequestBody ChartOfAccountDTO request) {

        try {
            chartOfAccountService.createChartOfAccount(request);

            return ResponseEntity.ok(
                    Map.of(
                            "success", true,
                            "message", "La cuenta ha sido creada exitosamente en el catálogo PUC"
                    )
            );

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error al guardar la información, intente nuevamente"
                    ));
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_CHART_OF_ACCOUNT')")
    public ResponseEntity<?> searchChartOfAccounts(@ModelAttribute ChartOfAccountDTO request, Pageable pageable) {

        try {
            Page<ChartOfAccount> result =
                    chartOfAccountService.searchChartOfAccounts(request, pageable);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "success", false,
                            "message", e.getMessage()
                    )
            );

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Error al consultar datos, intente nuevamente"
                    ));
        }
    }

}
