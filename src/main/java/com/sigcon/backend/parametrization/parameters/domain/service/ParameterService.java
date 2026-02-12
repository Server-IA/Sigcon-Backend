package com.sigcon.backend.parametrization.parameters.domain.service;

import com.sigcon.backend.parametrization.parameters.domain.model.ParameterDataTableRequest;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;

import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;

import com.sigcon.backend.parametrization.parameters.application.CreateParameterRequest;
import com.sigcon.backend.parametrization.parameters.application.ParameterDTO;
import com.sigcon.backend.parametrization.parameters.application.ParameterResponse;
import com.sigcon.backend.parametrization.parameters.application.UpdateParameterRequest;
import com.sigcon.backend.parametrization.parameters.application.UserParameterDTO;
import com.sigcon.backend.parametrization.parameters.domain.model.Parameter;
import com.sigcon.backend.parametrization.parameters.domain.model.UserParameter;
import com.sigcon.backend.parametrization.parameters.domain.model.enums.CategoryParameter;
import com.sigcon.backend.parametrization.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.parametrization.parameters.domain.repository.UserParameterRepository;
import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParameterService {

    private final ParameterRepository parameterRepository;
    private final UserParameterRepository userParameterRepository;
    private final UserRepository userRepository;

    private final DataTableSpecificationBuilder<Parameter> parameterSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    private final DataTableSpecificationBuilder<UserParameter> userParameterSpecificationBuilder =
    new DataTableSpecificationBuilder<>();

    /**
     * PA-RF-29: Visualización de parámetros por usuario
     */
    public ResponseEntity<?> getUserParameters(DataTableRequest dtRequest) {
        try {

            User user = getAuthenticatedUser();

            if (user == null) {
                return ResponseEntity.badRequest().body("Debe iniciar sesión para visualizar sus parámetros.");
            }

            // dtRequest.getColumns().add(new DataTableRequest.DataTableColumn(
            //     "user_id", "", true, true, 
            //     new DataTableRequest.DataTableSearch(user.getId().toString(), false)
            // ));

            int start = Math.max(0, dtRequest.getStart());
            int length = dtRequest.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<UserParameter> spec = userParameterSpecificationBuilder.build(dtRequest)
            .and((root, query, cb) -> cb.equal(root.get("user").get("id"), user.getId()))
            .and((root, query, cb) -> cb.isNull(root.get("deleted_at")));
            
            System.out.println("user id: " + user.getId().toString());
            
            Page<UserParameter> parameters = userParameterRepository.findAll(spec, pageable);

            

            return ResponseEntity.ok(
                DataTableResponse.from(parameters.map(userParameter -> UserParameterDTO.builder()
                    .id(userParameter.getId())
                    .user(UserDTO.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .email(user.getEmail())
                        .build())
                    .parameter_id(userParameter.getParameter().getId())
                    .value(userParameter.getValue())
                    .parameter(ParameterDTO.builder()
                        .id(userParameter.getParameter().getId())
                        .name(userParameter.getParameter().getName())
                        .category(userParameter.getParameter().getCategory())
                        .status(userParameter.getParameter().getStatus())
                        .build())
                    .created_at(userParameter.getCreated_at())
                    .updated_at(userParameter.getUpdated_at())
                    .build()), dtRequest.getDraw())
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        }
    }

    /**
     * PA-RF-30: Asignación / Creación de parámetros por usuario
     */
    @Transactional
    public ResponseEntity<?> createUserParameter(CreateParameterRequest request) {
        try {
            User user = getAuthenticatedUser();
            if (user == null) {
                return ResponseEntity.badRequest().body("Debe iniciar sesión para asignar parámetros.");
            }

            // Validar que el parámetro exista
            Parameter parameter = parameterRepository.findById(request.getParameterId())
                    .orElseThrow(() -> new RuntimeException("Parámetro no encontrado."));

            // Validar que no esté duplicado
            if (userParameterRepository.existsByUserAndParameter(user, parameter)) {
                return ResponseEntity.badRequest()
                        .body("Ya tiene un color asignado para este parámetro.");
            }

            // Validar color hexadecimal
            String validatedColor = validateHexColor(request.getColorValue());
            if (validatedColor == null) {
                return ResponseEntity.badRequest()
                        .body("El formato de hexadecimal no es válido");
            }

            // Validar que el valor sea obligatorio
            if (validatedColor.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("El valor del color es obligatorio");
            }

            UserParameter userParameter = UserParameter.builder()
                    .user(user)
                    .parameter(parameter)
                    .value(validatedColor)
                    .created_at(LocalDateTime.now())
                    .updated_at(LocalDateTime.now())
                    .build();

            userParameterRepository.save(userParameter);

            return ResponseEntity.ok("Parámetro asignado correctamente.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("No se pudo asignar el parámetro. Intente nuevamente o contacte al administrador.");
        }
    }

    /**
     * PA-RF-31: Edición de parámetros por usuario
     */
    @Transactional
    public ResponseEntity<?> updateUserParameter(Long parameterId, UpdateParameterRequest request) {
        try {
            User user = getAuthenticatedUser();
            if (user == null) {
                return ResponseEntity.badRequest().body("Debe iniciar sesión para editar parámetros.");
            }

            // Validar que el parámetro exista
            Parameter parameter = parameterRepository.findById(parameterId)
                    .orElseThrow(() -> new RuntimeException("Parámetro no encontrado."));

            // Buscar la asignación del usuario
            UserParameter userParameter = userParameterRepository.findByUserAndParameter(user, parameter)
                    .orElseThrow(() -> new RuntimeException("No tiene un color asignado para este parámetro."));

            // Validar color hexadecimal
            String validatedColor = validateHexColor(request.getColorValue());
            if (validatedColor == null) {
                return ResponseEntity.badRequest()
                        .body("El formato de hexadecimal no es válido");
            }

            // Validar que el valor sea obligatorio
            if (validatedColor.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Por favor valide que los campos estén debidamente diligenciados.");
            }

            // Actualizar el color
            userParameter.setValue(validatedColor);
            userParameter.setUpdated_at(LocalDateTime.now());
            userParameterRepository.save(userParameter);

            return ResponseEntity.ok("Parámetro actualizado correctamente.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("No se pudo actualizar el parámetro. Intente nuevamente o contacte al administrador.");
        }
    }

    /**
     * PA-RF-32: Eliminación de parámetros por usuario
     */
    @Transactional
    public ResponseEntity<?> deleteUserParameter(Long parameterId) {
        try {
            User user = getAuthenticatedUser();
            if (user == null) {
                return ResponseEntity.badRequest().body("Debe iniciar sesión para eliminar parámetros.");
            }

            // Validar que el parámetro exista
            Parameter parameter = parameterRepository.findById(parameterId)
                    .orElseThrow(() -> new RuntimeException("Parámetro no encontrado."));

            // Validar que el usuario tenga el parámetro asignado
            UserParameter userParameter = userParameterRepository.findByUserAndParameter(user, parameter)
                    .orElseThrow(() -> new RuntimeException("No tiene un color asignado para este parámetro."));

            // Eliminar la asignación
            userParameter.setDeleted_at(LocalDateTime.now());
            userParameterRepository.save(userParameter);

            return ResponseEntity.ok("Parámetro eliminado correctamente.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("No se pudo eliminar el parámetro. Intente nuevamente o contacte al administrador.");
        }
    }

    /**
     * Método auxiliar para obtener el usuario autenticado
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        User user = userRepository.findByEmail(email)
                .orElse(null);
        return user;
    }

    /**
     * Método auxiliar para validar y normalizar el formato hexadecimal
     * Acepta formatos: #FF5733, FF5733, #ff5733, ff5733
     * Retorna: #FF5733 (normalizado)
     */
    private String validateHexColor(String colorValue) {
        if (colorValue == null || colorValue.trim().isEmpty()) {
            return null;
        }

        String color = colorValue.trim().toUpperCase();

        // Remover el # si existe
        if (color.startsWith("#")) {
            color = color.substring(1);
        }

        // Validar que tenga máximo 6 caracteres
        if (color.length() > 6) {
            return null;
        }

        // Validar que solo contenga caracteres hexadecimales (0-9, A-F)
        if (!color.matches("^[0-9A-F]{1,6}$")) {
            return null;
        }

        // Normalizar a 6 caracteres (rellenar con ceros a la izquierda si es necesario)
        while (color.length() < 6) {
            color = "0" + color;
        }

        // Retornar con el # al inicio
        return "#" + color;
    }

    /**
     * Método auxiliar para mapear UserParameter a ParameterResponse
     */
    // private ParameterResponse mapToResponse(UserParameter userParameter) {
    //     return ParameterResponse.builder()
    //             .id(userParameter.getId())
    //             .parameterId(userParameter.getParameter().getId())
    //             .parameterName(userParameter.getParameter().getName())
    //             .parameterDescription(userParameter.getParameter().getDescription())
    //             .value(userParameter.getValue())
    //             .creationDate(userParameter.getCreationDate())
    //             .lastUpdateDate(userParameter.getLastUpdateDate())
    //             .build();
    // }

    /**
     * PA-RF-25: Visualizar parámetros del sistema (DataTables + filtros)
     */
    public ResponseEntity<?> getSystemParametersPaged(DataTableRequest request) {
        try {
            
            int start = Math.max(0, request.getStart());
            int length = request.getLength();

            int safeLength = length <= 0 ? 10 : length;
            int page = start / safeLength;

            Pageable pageable = length == -1
                ? Pageable.unpaged()
                : PageRequest.of(page, safeLength);

            Specification<Parameter> spec = parameterSpecificationBuilder.build(request)
                .and((root, query, cb) -> cb.isNull(root.get("deleted_at")));

            Page<Parameter> parameters = parameterRepository.findAll(spec, pageable);
            
            return ResponseEntity.ok(
                DataTableResponse.from(parameters.map(parameter -> ParameterDTO.builder()
                    .id(parameter.getId())
                    .name(parameter.getName())
                    .category(parameter.getCategory())
                    .status(parameter.getStatus())
                    .build()), request.getDraw())
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(e.getMessage())
                    );
        }
    }

    /**
     * PA-RF-26: Crear parámetro del sistema
     */
    public ResponseEntity<?> storeSystemParameter(@Valid Parameter request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondJson(bindingResult));
        }

        try {
            if (parameterRepository.existsByName(request.getName())) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage("El parámetro ya existe"));
            }

            request.setId(null);
            request.setDeleted_at(null);

            Parameter saved = parameterRepository.save(request);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(e.getMessage()));
        }
    }

    /**
     * PA-RF-27: Editar parámetro del sistema
     */
    public ResponseEntity<?> updateSystemParameter(@Valid Parameter request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(buildValidationErrors(bindingResult));
        }

        try {
            Parameter parameter = parameterRepository.findById(request.getId())
                    .orElse(null);

            if (parameter == null || parameter.getDeleted_at() != null) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage("El parámetro seleccionado no existe o ya fue eliminado")
                );
            }

            if (parameterRepository.existsByNameAndIdNot(request.getName(), request.getId())) {
                return ResponseEntity.badRequest()
                        .body(buildFieldError("name", "El nombre ingresado ya existe en otro parámetro"));
            }

            parameter.setName(request.getName());
            parameter.setDescription(request.getDescription());
            parameter.setCategory(request.getCategory());
            parameter.setStatus(request.getStatus());

            Parameter saved = parameterRepository.save(parameter);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(e.getMessage())
                    );
        }
    }

    /**
     * PA-RF-28: Eliminar parámetro del sistema (eliminación lógica)
     */
    public ResponseEntity<?> deleteSystemParameter(Long id) {
        try {
            Parameter parameter = parameterRepository.findById(id).orElse(null);

            if (parameter == null || parameter.getDeleted_at() != null) {
                return ResponseEntity.badRequest()
                        .body("El parámetro seleccionado no existe o ya fue eliminado");
            }

            parameter.setDeleted_at(LocalDateTime.now());
            parameterRepository.save(parameter);

            Map<String, Object> response = new HashMap<>();
            response.put("title", "OK");
            response.put("message", "Parámetro eliminado correctamente");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("No se pudo eliminar el parámetro. Intente nuevamente o contacte al administrador");
        }
    }

    // ===== Helpers (mismo estilo que Modules) =====

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private Map<String, Object> buildValidationErrors(BindingResult bindingResult) {
        List<Map<String, String>> errors = bindingResult.getFieldErrors()
                .stream()
                .map(error -> {
                    Map<String, String> err = new HashMap<>();
                    err.put("field", error.getField());
                    err.put("message", error.getDefaultMessage());
                    return err;
                })
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("title", "Error de validación");
        response.put("errors", errors);
        return response;
    }

    private Map<String, Object> buildFieldError(String field, String message) {
        Map<String, String> fieldError = new HashMap<>();
        fieldError.put("field", field);
        fieldError.put("message", message);

        List<Map<String, String>> errors = new ArrayList<>();
        errors.add(fieldError);

        Map<String, Object> response = new HashMap<>();
        response.put("title", "Error de validación");
        response.put("errors", errors);
        return response;
    }

}
