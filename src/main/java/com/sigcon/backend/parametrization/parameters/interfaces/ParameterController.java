package com.sigcon.backend.parametrization.parameters.interfaces;

import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import com.sigcon.backend.parametrization.parameters.domain.model.ParameterDataTableRequest;
import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;

import com.sigcon.backend.parametrization.parameters.application.CreateParameterRequest;
import com.sigcon.backend.parametrization.parameters.application.UpdateParameterRequest;
import com.sigcon.backend.parametrization.parameters.domain.service.ParameterService;
import com.sigcon.backend.utils.DataTableRequest;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parameters")
@RequiredArgsConstructor
public class ParameterController {

    private final ParameterService parameterService;

    /**
     * PA-RF-25: Visualización de parámetros del sistema (DataTables)
     * POST /api/parameters
     */

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_VIEW_PARAMETERS')")
    public ResponseEntity<?> getSystemParameters(@RequestBody(required = false) DataTableRequest dtRequest) {
        return parameterService.getSystemParametersPaged(dtRequest);
    }

    /**
     * PA-RF-26: Crear parámetro del sistema
     * POST /api/parameters/store
     */
    @PostMapping("/store")
    @PreAuthorize("hasAuthority('PERM_CREATE_PARAMETER')")
    public ResponseEntity<?> storeSystemParameter(@Valid @RequestBody Parameter request, BindingResult bindingResult) {
        return parameterService.storeSystemParameter(request, bindingResult);
    }

    /**
     * PA-RF-27: Editar parámetro del sistema
     * PUT /api/parameters/update
     */
    @PutMapping("/update")
    @PreAuthorize("hasAuthority('PERM_UPDATE_PARAMETER')")
    public ResponseEntity<?> updateSystemParameter(@Valid @RequestBody Parameter request, BindingResult bindingResult) {
        return parameterService.updateSystemParameter(request, bindingResult);
    }

    /**
     * PA-RF-28: Eliminar parámetro del sistema (lógico)
     * DELETE /api/parameters/delete/{id}
     */
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('PERM_DELETE_PARAMETER')")
    public ResponseEntity<?> deleteSystemParameter(@PathVariable Long id) {
        return parameterService.deleteSystemParameter(id);
    }

    /**
     * PA-RF-29: Visualización de parámetros por usuario
     * GET /api/parameters/user
     */
    @PostMapping("/user")
    public ResponseEntity<?> getUserParameters(@RequestBody(required = false) DataTableRequest dtRequest) {
        return parameterService.getUserParameters(dtRequest);
    }

    /**
     * PA-RF-30: Asignación / Creación de parámetros por usuario
     * POST /api/parameters/user
     */
    @PostMapping("/user/create")
    public ResponseEntity<?> createUserParameter(@RequestBody CreateParameterRequest request) {
        return parameterService.createUserParameter(request);
    }

    /**
     * PA-RF-31: Edición de parámetros por usuario
     * PUT /api/parameters/user/{parameterId}
     */
    @PutMapping("/user/{parameterId}")
    public ResponseEntity<?> updateUserParameter(
            @PathVariable Long parameterId,
            @RequestBody UpdateParameterRequest request) {
        return parameterService.updateUserParameter(parameterId, request);
    }

    /**
     * PA-RF-32: Eliminación de parámetros por usuario
     * DELETE /api/parameters/user/{parameterId}
     */
    @DeleteMapping("/user/{parameterId}")
    public ResponseEntity<?> deleteUserParameter(@PathVariable Long parameterId) {
        return parameterService.deleteUserParameter(parameterId);
    }
}
