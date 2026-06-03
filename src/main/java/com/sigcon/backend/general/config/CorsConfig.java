package com.sigcon.backend.general.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();


        config.setAllowedOrigins(List.of(
            "http://localhost:5173",
            "http://165.22.166.82:5173/",
            "http://138.197.202.104:5173/",
            "${CORS_ALLOWED_ORIGINS:http://localhost:3000}",
            "http://localhost:3000",
            "https://www.inmero.co",
            "https://www.inmero.co/sigcon",
            "https://inmero.co/sigcon",
            "https://inmero.co"
        ));



        // QA Bloque AT (HU-PA-22, 2026-05-13): faltaba PATCH en la lista de
        // metodos permitidos. Resultado: cualquier endpoint PATCH (ej.
        // PATCH /api/parametrization/notifications/read-all,
        // PATCH /api/v1/cash-flow-projections/{id}/inactivate, etc.) fallaba
        // su preflight CORS con 403 "Invalid CORS request" cuando el frontend
        // intentaba consumirlo desde el navegador. El curl directo SI
        // funcionaba (no requiere preflight). Sintoma visible: "Sin conexion"
        // tras retry en fetchHelper.patch porque el preflight 403 dispara
        // "Failed to fetch" -> retry -> mismo error -> dialog "Sin conexion".
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));


        // PA-RNF-11 (Pendientes PA 2026-05-30): headers requeridos por el contrato.
        // X-API-Key (AAEF), Idempotency-Key (alta de empresa) y X-Requested-With.
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept",
                "X-API-Key", "Idempotency-Key", "X-Requested-With"));


        config.setAllowCredentials(true);
        // PA-RNF-11: cache del preflight (OPTIONS) por 1 hora para reducir round-trips.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

}
