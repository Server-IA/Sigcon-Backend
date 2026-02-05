package com.sigcon.backend.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        response.put("title", "Error de validación");
        response.put("errors", errors);
        return response;
    }

    public static Map<String, Object> getErrorRespondMessage(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("title", "Error interno");
        response.put("message", message);
        return response;
    }

}
