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

import io.swagger.v3.oas.annotations.Hidden;

@Hidden

@RestControllerAdvice

public class GlobalExceptionHandler  {

    ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> CONSTRAINT_MESSAGES = new HashMap<>();

    private static class ConstraintMessages {
        private String code;
        private String message;

        public String getMessage() { return message; }
        public String getCode() { return code; }

        public void setMessage(String message) { this.message = message; }
        public void setCode(String code) { this.code = code; }
    }



    @PostConstruct
    public void loadConstraintMessages() {

        try {

            Resource[] resources =
                new PathMatchingResourcePatternResolver()
                    .getResources("classpath:jsons/*.json");

            for (Resource resource : resources) {

                System.out.println("Procesando archivo: " + resource.getFilename());

                var list = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<java.util.List<ConstraintMessages>>() {}
                );

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
        if (rootMessage != null && rootMessage.contains("Debe existir al menos un usuario con SUPERADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("No se puede eliminar o modificar el único SUPERADMIN")
                        )
                    );
        }

        if (errorMessage.isPresent()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(errorMessage)
                    );
        }

        // if (rootMessage != null && rootMessage.contains("UP0001")) {
        //     return ResponseEntity
        //             .status(HttpStatus.BAD_REQUEST)
        //             .body(
        //                 ErrorRespondJson.getErrorRespondMessage(Optional.of("Debe existir al menos un usuario con SUPERADMIN"))
        //             );
        // }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of("Error de integridad de datos."))
                );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {

        System.out.println("ex.getMessage(): " + ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
                );
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> handleIllegalStateException(IllegalStateException ex) {

        System.out.println("ex.getMessage() IllegalStateException: " + ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(Optional.of(ex.getMessage()))
                );
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception ex) {

        String message = ex.getMessage();

        System.out.println("ex.getMessage() Exception: " + message);

        if (message != null && message.contains("Debe existir al menos un usuario con SUPERADMIN")) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                        ErrorRespondJson.getErrorRespondMessage(
                            Optional.of("Debe existir al menos un usuario con SUPERADMIN.")
                        )
                    );
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                    ErrorRespondJson.getErrorRespondMessage(
                        Optional.of(message != null ? message : "Error interno del servidor.")
                    )
                );
    }


    
    
}
