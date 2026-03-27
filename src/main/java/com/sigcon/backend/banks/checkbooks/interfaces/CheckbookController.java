package com.sigcon.backend.banks.checkbooks.interfaces;

import com.sigcon.backend.banks.checkbooks.application.*;
import com.sigcon.backend.banks.checkbooks.domain.service.CheckbookService;
import com.sigcon.backend.utils.SuccessRespondJson;
import com.sigcon.backend.utils.ErrorRespondJson;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/checkbooks")
@RequiredArgsConstructor
@Tag(name = "Chequeras", description = "Endpoints para gestión de chequeras")
public class CheckbookController {

    private final CheckbookService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CREATE_CHECKBOOK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> create(@RequestBody CheckbookRequest request) {
        Object response = service.save(request, null);

        if (response instanceof ErrorRespondJson) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Chequera creada correctamente"),
                        Optional.of(response)
                )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_UPDATE_CHECKBOOK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CheckbookRequest request) {
        Object response = service.save(request, id);

        if (response instanceof ErrorRespondJson) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Chequera actualizada correctamente"),
                        Optional.of(response)
                )
        );
    }

    @PostMapping("/delete")
    @PreAuthorize("hasAuthority('PERM_DELETE_CHECKBOOK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> delete(@RequestBody CheckbookDeleteRequest request) {
        Object response = service.delete(request);

        if (response instanceof ErrorRespondJson) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Operación realizada correctamente"),
                        Optional.of(response)
                )
        );
    }

    @PostMapping("/search")
    @PreAuthorize("hasAuthority('PERM_VIEW_CHECKBOOK') or hasAuthority('ROLE_SUPERADMIN')")
    public ResponseEntity<?> search(@RequestBody(required = false) CheckbookQueryRequest request) {
        Object response = service.search(request != null ? request : new CheckbookQueryRequest());

        if (response instanceof ErrorRespondJson) {
            return ResponseEntity.badRequest().body(response);
        }

        return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(
                        Optional.of("Consulta realizada correctamente"),
                        Optional.of(response)
                )
        );
    }
}