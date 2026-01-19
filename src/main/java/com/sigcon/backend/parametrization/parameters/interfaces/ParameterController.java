package com.sigcon.backend.parametrization.parameters.interfaces;

import com.sigcon.backend.parametrization.parameters.application.CreateParameterRequest;
import com.sigcon.backend.parametrization.parameters.application.UpdateParameterRequest;
import com.sigcon.backend.parametrization.parameters.domain.service.ParameterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parameters")
@RequiredArgsConstructor
public class ParameterController {

    private final ParameterService parameterService;

    /**
     * PA-RF-29: Visualización de parámetros por usuario
     * GET /api/parameters/user
     */
    @GetMapping("/user")
    public ResponseEntity<?> getUserParameters(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        return parameterService.getUserParameters(pageable);
    }

    /**
     * PA-RF-30: Asignación / Creación de parámetros por usuario
     * POST /api/parameters/user
     */
    @PostMapping("/user")
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
