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
            "https://www.inmero.co/sigcon"
        ));



        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));


        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));


        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

}
