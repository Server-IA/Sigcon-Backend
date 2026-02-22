package com.sigcon.backend.general.security;

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

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final BlackListFilter blackListFilter;
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
                    authorize.requestMatchers("/auth/**", "/users/avatar/**").permitAll();

                    // Permitir acceso a Swagger/OpenAPI solo en perfil dev
                    if (isDev) {
                        authorize.requestMatchers(
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/v3/api-docs.yaml"
                        ).permitAll();
                    }

                    // Todos los demás endpoints requieren autenticación
                    authorize.anyRequest().authenticated();
                })

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authenticationProvider(authenticationProvider)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        http.addFilterBefore(blackListFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);



        return http.build();

    }

}