package com.sigcon.backend.integration.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HU-INT-RF-01 E4: Valida que el {@code Content-Length} del request a
 * {@code /api/contabilidad/aaef} no exceda 20 MB. Responde 413 con mensaje
 * exacto del Excel ANTES de que Spring intente leer el body.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AaefPayloadSizeFilter extends OncePerRequestFilter {

    /** 20 MB en bytes. */
    private static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final String AAEF_PATH = "/api/contabilidad/aaef";

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AAEF_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length > MAX_BYTES) {
            response.setStatus(413);
            response.setContentType("application/json");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("code", 413);
            body.put("error", "Payload demasiado grande");
            body.put("message", "El lote supera el tamaño máximo permitido (20 MB)");
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }
        chain.doFilter(request, response);
    }
}
