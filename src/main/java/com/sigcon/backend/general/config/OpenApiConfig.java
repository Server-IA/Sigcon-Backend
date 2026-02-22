package com.sigcon.backend.general.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components())
                .info(new Info()
                        .title("SIGCON Backend API")
                        .version("v1")
                        .description("Documentación de la API backend de SIGCON")
                        .contact(new Contact().name("SIGCON Team").email("noreply@sigcon.local"))
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))
                );
    }
}
