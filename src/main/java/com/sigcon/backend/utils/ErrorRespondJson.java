package com.sigcon.backend.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.validation.BindingResult;

public class ErrorRespondJson {

    public static Map<String, Object> getErrorRespondJson(BindingResult bindingResult) {
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
        response.put("success", false);
        response.put("code", 400);
        response.put("error", "Error en la operación");
        response.put("message", "Error de validación");
        response.put("details", errors);
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return response;
    }

    public static Map<String, Object> getErrorRespondMessage(Optional<String> message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("code", 400);
        response.put("error", "Error en la operación");
        response.put("message", message.orElse("Error en la operación"));
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("data", null);
        return response;
    }

}
