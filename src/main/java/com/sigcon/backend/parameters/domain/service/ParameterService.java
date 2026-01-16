package com.sigcon.backend.parameters.domain.service;

import com.sigcon.backend.parameters.application.CreateParameterRequest;
import com.sigcon.backend.parameters.application.ParameterResponse;
import com.sigcon.backend.parameters.application.UpdateParameterRequest;
import com.sigcon.backend.parameters.domain.model.Parameter;
import com.sigcon.backend.parameters.domain.model.UserParameter;
import com.sigcon.backend.parameters.domain.repository.ParameterRepository;
import com.sigcon.backend.parameters.domain.repository.UserParameterRepository;
import com.sigcon.backend.users.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    /**
     * PA-RF-29: Visualización de parámetros por usuario
     */
    public ResponseEntity<?> getUserParameters(Pageable pageable) {
        try {
            User user = getAuthenticatedUser();
            if (user == null) {
                return ResponseEntity.badRequest().body("Debe iniciar sesión para visualizar sus parámetros.");
            }

            Page<UserParameter> userParametersPage = userParameterRepository.findByUser(user, pageable);
            List<ParameterResponse> responses = userParametersPage.getContent().stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());

            Page<ParameterResponse> responsePage = new PageImpl<>(responses, pageable, userParametersPage.getTotalElements());

            return ResponseEntity.ok(responsePage);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("No se pudieron cargar los parámetros. Intente nuevamente o contacte al administrador.");
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
                    .colorValue(validatedColor)
                    .creationDate(LocalDateTime.now())
                    .lastUpdateDate(LocalDateTime.now())
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
            userParameter.setColorValue(validatedColor);
            userParameter.setLastUpdateDate(LocalDateTime.now());
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
            userParameterRepository.delete(userParameter);

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return (User) authentication.getPrincipal();
        }
        return null;
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
    private ParameterResponse mapToResponse(UserParameter userParameter) {
        return ParameterResponse.builder()
                .id(userParameter.getId())
                .parameterId(userParameter.getParameter().getId())
                .parameterName(userParameter.getParameter().getName())
                .parameterDescription(userParameter.getParameter().getDescription())
                .colorValue(userParameter.getColorValue())
                .creationDate(userParameter.getCreationDate())
                .lastUpdateDate(userParameter.getLastUpdateDate())
                .build();
    }
}
