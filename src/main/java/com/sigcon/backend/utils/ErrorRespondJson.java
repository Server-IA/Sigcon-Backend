package com.sigcon.backend.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.validation.BindingResult;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Respuesta de error de la API")

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class ErrorRespondJson {

    @Schema(description = "Indica si la operación fue exitosa")
    private boolean success;

    @Schema(description = "Código HTTP de error")
    private int code;

    @Schema(description = "Descripción corta del error")
    private String error;

    @Schema(description = "Mensaje detallado del error")
    private String message;

    @Schema(description = "Detalles adicionales, como errores de validación")
    private List<Map<String, String>> details;

    @Schema(description = "Marca de tiempo del error")
    private String timestamp;

    @Schema(description = "Datos retornados (usualmente null en errores)")
    private Object data;

    // @Schema(description = "Respuesta de error de la API, contiene los errores de validación")
    // public static ErrorRespondJson getErrorRespondJson(BindingResult bindingResult) {
    //     List<Map<String, String>> errors = bindingResult.getFieldErrors()
    //         .stream()
    //         .map(error -> {
    //             Map<String, String> err = new HashMap<>();
    //             err.put("field", error.getField());
    //             err.put("message", error.getDefaultMessage());
    //             return err;
    //         })
    //         .toList();

    //     Map<String, Object> response = new HashMap<>();
    //     response.put("success", false);
    //     response.put("code", 400);
    //     response.put("error", "Error en la operación");
    //     response.put("message", "Error de validación");
    //     response.put("details", errors);
    //     response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    //     return response;
    // }

    // @Schema(description = "Respuesta de error de la API, contiene el mensaje de error")
    // public static Map<String, Object> getErrorRespondMessage(Optional<String> message) {
    //     Map<String, Object> response = new HashMap<>();
    //     response.put("success", false);
    //     response.put("code", 400);
    //     response.put("error", "Error en la operación");
    //     response.put("message", message.orElse("Error en la operación"));
    //     response.put("details", null);
    //     response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    //     response.put("data", null);
    //     return response;
    // }

    public ErrorRespondJson(boolean success, int code, String error, String message, List<Map<String, String>> details, Object data) {
        this.success = success;
        this.code = code;
        this.error = error;
        this.message = message;
        this.details = details;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.data = data;
    }

    // Métodos estáticos para crear errores
    public static ErrorRespondJson getErrorRespondMessage(Optional<String> message) {
        return new ErrorRespondJson(false, 400, "Error en la operación", message.orElse("Error en la operación"), null, null);
    }

    public static ErrorRespondJson getErrorRespondJson(BindingResult bindingResult) {
        List<Map<String, String>> errors = bindingResult.getFieldErrors()
            .stream()
            .map(error -> {
                Map<String, String> err = new HashMap<>();
                err.put("field", error.getField());
                err.put("message", error.getDefaultMessage());
                return err;
            })
            .toList();
        // Bloque AN (2026-05-04): si solo hay 1 error de campo, exponer el mensaje
        // literal del field error en `message`. Asi los clientes que solo leen
        // `message` (no `errors[]`) ven el texto literal del HU en vez del
        // generico "Error de validacion". Cuando hay multiples errores se
        // mantiene el generico para no esconder informacion.
        String topMsg = errors.size() == 1 ? errors.get(0).get("message") : "Error de validación";
        return new ErrorRespondJson(false, 400, "Error en la operación", topMsg, errors, null);
    }

}
