package com.sigcon.backend.invoices.interfaces;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sigcon.backend.invoices.application.InvoiceFCRequestDTO;
import com.sigcon.backend.invoices.domain.model.Invoices;
import com.sigcon.backend.invoices.domain.service.InvoiceService;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RequestMapping("/api/v1/invoices")
@RestController
@RequiredArgsConstructor

@Tag(name = "Facturas", description = "Endpoints para gestion de facturas")
public class InvoicesController {

    private final InvoiceService invoicesService;

    @Operation(summary = "Crear factura FC", description = "Crea una nueva factura FC")
    @PostMapping("/fc")
    @PreAuthorize("hasAuthority('PERM_CREATE_INVOICE_FC') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> createInvoiceFC(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Datos de la factura FC") InvoiceFCRequestDTO invoiceFCRequestDTO,
        BindingResult bindingResult
    ) {
        if(bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                .body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }
        Invoices invoice = invoicesService.createInvoice(invoiceFCRequestDTO, 1L);

        return ResponseEntity.ok(invoice);
    }

    @Operation(summary = "Consultar facturas")
    @PostMapping("/search")

    @PreAuthorize("hasAuthority('PERM_READ_INVOICE') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> getInvoices(@RequestBody(required = false) DataTableRequest request) {
        
        return invoicesService.getInvoices(request);
    }

}
