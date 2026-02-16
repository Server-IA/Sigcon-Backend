package com.sigcon.backend.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SuccessRespondJson {
    public static Map<String, Object> getSuccessRespondMessage(Optional<String> message, Optional<Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("code", 200);
        response.put("message", message.orElse("Operación realizada correctamente"));
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("data", data.orElse(null));
        return response;
    }
}
