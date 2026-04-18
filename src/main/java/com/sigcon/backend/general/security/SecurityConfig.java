package com.sigcon.backend.general.security;

import com.sigcon.backend.integration.infrastructure.security.AaefPayloadSizeFilter;
import com.sigcon.backend.integration.infrastructure.security.AgroFusionJwtFilter;
import com.sigcon.backend.integration.infrastructure.security.ApiKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final BlackListFilter blackListFilter;
    private final ApiKeyFilter apiKeyFilter;
    private final AgroFusionJwtFilter agroFusionJwtFilter;
    private final AaefPayloadSizeFilter aaefPayloadSizeFilter;
    private final Environment environment;


    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        grantedAuthoritiesConverter.setAuthoritiesClaimName("authorities");
        grantedAuthoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return authenticationConverter;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // Determinar si estamos en perfil dev (debe ser final para usar en lambda)
        final boolean isDev;
        boolean tempDev = false;
        try {
            String[] activeProfiles = environment.getActiveProfiles();
            for (String p : activeProfiles) {
                if ("dev".equalsIgnoreCase(p)) {
                    tempDev = true;
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        isDev = tempDev;

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> {
                    // Auth endpoints públicos
                    authorize.requestMatchers("/auth/**", "/users/avatar/**", "/api/v1/companies/logo/**").permitAll();

                    // HU-INT-RF-08: Endpoint de salud publico para que AgroFusion verifique disponibilidad
                    authorize.requestMatchers("/api/contabilidad/health").permitAll();

                    // HU-INT-RF-11 (operacion): endpoints administrativos protegidos por JWT del
                    // SSO interno + @PreAuthorize("hasAuthority('ROLE_ADMIN')"). Estos NO
                    // permiten autenticacion por X-API-Key ni JWT de AgroFusion - solo el admin
                    // contable autenticado en SIGCON puede invocarlos.
                    // IMPORTANTE: este matcher debe ir ANTES de /api/contabilidad/** para ganar
                    // por orden de evaluacion.
                    authorize.requestMatchers("/api/contabilidad/admin/**").authenticated();

                    // HU-INT-RF-14/15: Endpoints del frontend admin (BatchManagementController).
                    // Los consume el contador autenticado con JWT del SSO interno (HS256).
                    // El AgroFusionJwtFilter y ApiKeyFilter los excluyen; aqui pedimos authenticated()
                    // para que oauth2ResourceServer los valide con el JWT interno estandar.
                    authorize.requestMatchers("/api/contabilidad/lotes/**",
                                              "/api/contabilidad/transferencias/**").authenticated();

                    // HU-INT-RF-01/09/10/11/12: Endpoints de integracion AAEF.
                    // La autenticacion la hacen AgroFusionJwtFilter (Bearer JWT RS256,
                    // HU-INT-RF-11) y ApiKeyFilter (X-API-Key como fallback, HU-INT-RF-12).
                    // Spring Security permite el request; los filtros devuelven 401/403 si falla.
                    authorize.requestMatchers("/api/contabilidad/**").permitAll();

                    // HU-INT-RF-11: Mock IdP para desarrollo/smoke test (JWKS + token generator).
                    // En produccion apunta al IdP real y este endpoint debe deshabilitarse.
                    authorize.requestMatchers("/mock-idp/**").permitAll();

                    // Fase 7 + HU-INT-RF-13: Mock callback de AgroFusion para validar ACK + retry
                    // mientras AgroFusion no implemente su endpoint real.
                    authorize.requestMatchers("/mock-agrofusion/**").permitAll();

                    // Swagger/OpenAPI publico (documentacion de la API - util en todos los
                    // perfiles para QA, integracion con AgroFusion y evaluacion academica).
                    // En produccion real se protege a nivel de red (firewall/ingress).
                    authorize.requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs.yaml"
                    ).permitAll();

                    // Todos los demás endpoints requieren autenticación
                    authorize.anyRequest().authenticated();
                })

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authenticationProvider(authenticationProvider)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        http.addFilterBefore(blackListFilter, UsernamePasswordAuthenticationFilter.class);

        // HU-INT-RF-11/12: Orden de filtros en /api/contabilidad/** es importante:
        //   1. AgroFusionJwtFilter (JWT RS256 con JWKS) - tiene prioridad
        //   2. ApiKeyFilter (X-API-Key) - fallback si no hay JWT o JWT no valida
        // Si el JWT es valido, ApiKeyFilter detecta auth ya establecida y se salta.
        // Si el JWT esta presente pero falla, el filtro responde 401/403 y NO llega a API Key.
        // HU-INT-RF-01/11/12: orden de filtros para /api/contabilidad/**
        // addFilterBefore(X, UPA) inserta X INMEDIATAMENTE antes de UPA. Si se
        // llama multiples veces con la misma referencia, el ULTIMO llamado queda
        // MAS cerca de UPA. Para lograr el orden de ejecucion:
        //   payloadSize -> jwt -> apiKey -> UPA
        // se llama en ese mismo orden (payloadSize primero, apiKey ultimo):
        http.addFilterBefore(aaefPayloadSizeFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(agroFusionJwtFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);



        return http.build();

    }

}