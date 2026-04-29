package com.sigcon.backend.general.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sigcon.backend.utils.ErrorRespondJson;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;

@Hidden

@RestControllerAdvice

public class GlobalExceptionHandler {

    ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> CONSTRAINT_MESSAGES = new HashMap<>();

    private static class ConstraintMessages {
        private String code;
        private String message;

        public String getMessage() {
            return message;
        }

        public String getCode() {
            return code;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    @PostConstruct
    public void loadConstraintMessages() {

        try {

            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:jsons/*.json");

            for (Resource resource : resources) {

                System.out.println("Procesando archivo: " + resource.getFilename());

                var list = objectMapper.readValue(
                        resource.getInputStream(),
                        new TypeReference<java.util.List<ConstraintMessages>>() {
                        });

                for (ConstraintMessages item : list) {
                    CONSTRAINT_MESSAGES.put(item.code, item.message);
                }

            }

            System.out.println("✔ Mensajes de constraints cargados: " + CONSTRAINT_MESSAGES.size());

        } catch (Exception e) {
            System.out.println("⚠ Error leyendo JSON: " + e.getMessage());
            throw new RuntimeException("Error leyendo archivos JSON", e);
        }
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        Throwable root = ex.getRootCause();

        String rootMessage = root != null
                ? root.getMessage()
                : ex.getMessage();

        System.out.println("rootMessage: " + rootMessage);

        Optional<String> errorMessage = CONSTRAINT_MESSAGES.entrySet()
                .stream()
                .filter(entry -> rootMessage != null && rootMessage.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();

        // Detectar mensaje del trigger directamente
        if (rootMessage != null && rootMessage.contains("Debe existir al menos un usuario con ADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            ErrorRespondJson.getErrorRespondMessage(
                                    Optional.of("No se puede eliminar o modificar el único ADMIN")));
        }

        if (errorMessage.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            ErrorRespondJson.getErrorRespondMessage(errorMessage));
        }

        // if (rootMessage != null && rootMessage.contains("UP0001")) {
        // return ResponseEntity
        // .status(HttpStatus.BAD_REQUEST)
        // .body(
        // ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe existir al menos un
        // usuario con ADMIN"))
        // );
        // }

        // ERR-MNT-TER-003: Mensaje descriptivo en vez de genérico
        String descriptiveMessage = "Error de integridad de datos.";
        if (rootMessage != null) {
            if (rootMessage.contains("third_part") || rootMessage.contains("tercero"))
                descriptiveMessage = "No es posible realizar esta operación porque el tercero tiene registros asociados en otros módulos. Verifique y retire las dependencias antes de continuar.";
            else if (rootMessage.contains("compan") || rootMessage.contains("empresa"))
                descriptiveMessage = "No es posible realizar esta operación porque la empresa tiene registros asociados.";
            else if (rootMessage.contains("cost_center") || rootMessage.contains("centro"))
                descriptiveMessage = "No es posible realizar esta operación porque el centro de costo tiene registros asociados.";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(descriptiveMessage)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {

        System.out.println("ex.getMessage(): " + ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage())));
    }

    /**
     * HU-TENANT-01: al intentar acceder a un recurso de otra empresa por PK,
     * devolvemos 404 (no 403) para no revelar la existencia del recurso en
     * otra empresa. Disparado por @PostLoad listener de entidades
     * tenant-scoped.
     */
    @ExceptionHandler(com.sigcon.backend.platform.tenant.TenantIsolationException.class)
    public ResponseEntity<?> handleTenantIsolation(
            com.sigcon.backend.platform.tenant.TenantIsolationException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Recurso no encontrado")));
    }

    /**
     * HU-AP-02 E3: Optimistic locking. Cuando dos usuarios cargan la misma
     * factura simultaneamente y uno guarda primero, el segundo recibe
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (wrapper de Spring sobre {@link jakarta.persistence.OptimisticLockException}).
     * Devolvemos HTTP 409 Conflict con el mensaje exacto de la HU para que el
     * usuario sepa que debe recargar y reintentar.
     */
    @ExceptionHandler({
            org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class
    })
    public ResponseEntity<?> handleOptimisticLock(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of("Esta factura fue modificada por otro usuario. "
                                + "Recarga los datos y vuelve a intentarlo.")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException ex) {

        System.out.println("ex.getMessage() IllegalStateException: " + ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage())));
    }

    /** Captura RuntimeException como HTTP 400 (errores de negocio) */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException ex) {
        System.out.println("RuntimeException: " + ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(ex.getMessage() != null ? ex.getMessage() : "Error en la operación.")));
    }

    /** Captura NoSuchElementException como HTTP 404 (recurso no encontrado) */
    @ExceptionHandler(java.util.NoSuchElementException.class)
    public ResponseEntity<?> handleNoSuchElement(java.util.NoSuchElementException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(ex.getMessage() != null ? ex.getMessage() : "Recurso no encontrado.")));
    }

    /**
     * Captura AccessDeniedException como HTTP 403 (sin permisos).
     *
     * <p>HU-AU-07 E2 / HU-AU-08 E2 / HU-AU-09 E6 (2026-04-28): mensaje contextual
     * segun el endpoint accedido. Para rutas del modulo Auditoria devuelve
     * el mensaje exacto que pide la HU correspondiente.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex,
            jakarta.servlet.http.HttpServletRequest request) {
        String path = request != null ? request.getRequestURI() : "";
        String message;
        if (path.contains("/audit/dashboard")) {
            message = "Acceso restringido al dashboard";
        } else if (path.contains("/audit/logs/journal-entry")
                || path.contains("/audit/logs/entity")) {
            message = "Acceso restringido para vinculacion";
        } else if (path.contains("/audit")) {
            message = "Acceso denegado";
        } else {
            message = "No tiene permisos para realizar esta acción.";
        }
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ErrorRespondJson.getErrorRespondMessage(Optional.of(message)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {

        String message = ex.getMessage();

        System.out.println("ex.getMessage() Exception: " + message);

        if (message != null && message.contains("Debe existir al menos un usuario con ADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            ErrorRespondJson.getErrorRespondMessage(
                                    Optional.of("Debe existir al menos un usuario con ADMIN.")));
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ErrorRespondJson.getErrorRespondMessage(
                                Optional.of(message != null ? message : "Error interno del servidor.")));
    }

}
