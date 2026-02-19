package com.sigcon.backend.parametrization.parameters.domain.service;

import com.sigcon.backend.parametrization.parameters.domain.model.ParameterDataTableRequest;
import com.sigcon.backend.utils.DataTableRequest;
import com.sigcon.backend.utils.DataTableResponse;
import com.sigcon.backend.utils.DataTableSpecificationBuilder;
import com.sigcon.backend.utils.ErrorRespondJson;
import com.sigcon.backend.utils.SuccessRespondJson;

import org.springframework.validation.BindingResult;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import com.sigcon.backend.parametrization.users.application.role.PermissionDTO;
import com.sigcon.backend.parametrization.users.application.user.UserDTO;
import com.sigcon.backend.parametrization.users.domain.model.Role;
import com.sigcon.backend.parametrization.users.domain.model.User;
import com.sigcon.backend.parametrization.users.domain.repository.PermissionRepository;
import com.sigcon.backend.parametrization.users.domain.repository.UserRepository;
import com.sigcon.backend.parametrization.users.domain.service.UserService;

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
    private final PermissionRepository permissionRepository;

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
                        .body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of("El parámetro ya tiene un valor asignado."))
                        );
            }

            // Validar color hexadecimal
            String validatedColor = validateHexColor(request.getColorValue());
            if (validatedColor == null) {
                return ResponseEntity.badRequest()
                        .body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of("El formato de hexadecimal no es válido"))
                        );
            }

            // Validar que el valor sea obligatorio
            if (validatedColor.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of("El valor del color es obligatorio"))
                        );
            }

            UserParameter userParameter = UserParameter.builder()
                    .user(user)
                    .parameter(parameter)
                    .value(validatedColor)
                    .created_at(LocalDateTime.now())
                    .updated_at(LocalDateTime.now())
                    .build();

            userParameterRepository.save(userParameter);
            

            UserDTO userDto = getUserDTO(user);

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Parámetro asignado correctamente"), Optional.of(userDto)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("No se pudo asignar el parámetro. Intente nuevamente o contacte al administrador.")));
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
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe iniciar sesión para editar parámetros."))
                );
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
                        .body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of("El formato de hexadecimal no es válido"))
                        );
            }

            // Validar que el valor sea obligatorio
            if (validatedColor.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(
                            ErrorRespondJson.getErrorRespondMessage(Optional.of("Por favor valide que los campos estén debidamente diligenciados."))
                        );
            }

            // Actualizar el color
            userParameter.setValue(validatedColor);
            userParameter.setUpdated_at(LocalDateTime.now());
            userParameterRepository.save(userParameter);

            UserDTO userDto = getUserDTO(user);

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Parámetro actualizado correctamente"), Optional.of(userDto)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("No se pudo actualizar el parámetro. Intente nuevamente o contacte al administrador.")));
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

            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Parámetro eliminado correctamente"), Optional.empty()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("No se pudo eliminar el parámetro. Intente nuevamente o contacte al administrador.")));
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
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));

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
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
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
            if (parameterRepository.existsByNameAndCategoryAndDeletedAtIsNull(request.getName(), request.getCategory())) {
                return ResponseEntity.badRequest().body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El parámetro con el nombre " + request.getName() + " y categoría " + request.getCategory() + " ya existe")));
            }

            request.setId(null);
            request.setDeletedAt(null);

            Parameter saved = parameterRepository.save(request);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Parámetro creado correctamente"), Optional.of(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage())));
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

            if (parameter == null || parameter.getDeletedAt() != null) {
                return ResponseEntity.badRequest().body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("El parámetro seleccionado no existe o ya fue eliminado"))
                );
            }

            if (parameterRepository.existsByNameAndCategoryAndIdNot(request.getName(), request.getCategory(), request.getId())) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El parámetro con el nombre " + request.getName() + " y categoría " + request.getCategory() + " ya existe")));
            }

            parameter.setName(request.getName());
            parameter.setDescription(request.getDescription());
            parameter.setCategory(request.getCategory());
            parameter.setStatus(request.getStatus());

            Parameter saved = parameterRepository.save(parameter);
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Parámetro actualizado correctamente"), Optional.of(saved)));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(e.getMessage()))
                    );
        }
    }

    /**
     * PA-RF-28: Eliminar parámetro del sistema (eliminación lógica)
     */
    public ResponseEntity<?> deleteSystemParameter(Long id) {
        try {
            Parameter parameter = parameterRepository.findById(id).orElse(null);

            if (parameter == null || parameter.getDeletedAt() != null) {
                return ResponseEntity.badRequest()
                        .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("El parámetro seleccionado no existe o ya fue eliminado")));
            }

            parameter.setDeletedAt(LocalDateTime.now());
            parameterRepository.save(parameter);

            Map<String, Object> response = new HashMap<>();
            response.put("title", "OK");
            response.put("message", "Parámetro eliminado correctamente");
            return ResponseEntity.ok(
                SuccessRespondJson.getSuccessRespondMessage(Optional.of("Parámetro eliminado correctamente"), Optional.of(parameter)));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorRespondJson.getErrorRespondMessage(Optional.of("No se pudo eliminar el parámetro. Intente nuevamente o contacte al administrador")));
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

    private UserDTO getUserDTO(User  user) {
        return UserDTO.builder()
            .id(user.getId())
            .name(user.getName())
            .lastname(user.getLastname())
            .email(user.getEmail())
            .avatar(user.getAvatar())
            .status(user.getStatus())
            .roles(
                user.getRoles()
                    .stream()
                    .map(Role::getName)
                    .collect(Collectors.toSet())
                )
                .permissions(
                    permissionRepository.findByUserID(user.getId())
                    .stream()
                    .map(permission -> new PermissionDTO(
                        null,
                        permission.getName(),
                        permission.getCode(),
                        permission.getType(),
                        null,
                        null,
                        permission.getDescription(),
                        null
                    ))
                    .collect(Collectors.toList())
                )
                .parameters(
                    parameterRepository.findAll()
            .stream()
            .map(p -> new ParameterDTO(
                        null,
                        p.getName(),
                        p.getValue(),
                        userParameterRepository.findByUserAndParameter(user, p)
                            .map(up -> new UserParameterDTO(
                                null,
                                null,
                                null,
                                up.getValue(),
                                null,
                                null,
                                null,
                                null
                            )).orElse(null),
                        p.getCategory(),
                        p.getStatus(),
                        null,
                        null,
                        null
                    ))
                    .collect(Collectors.toList())
                )
                .build();
    }

}
