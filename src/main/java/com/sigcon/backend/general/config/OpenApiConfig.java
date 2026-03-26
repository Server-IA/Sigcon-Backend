package com.sigcon.backend.general.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "SIGCON API", version = "1.0"), security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .components(new Components())
                                .info(new Info()
                                                .title("SIGCON Backend API")
                                                .version("v1")
                                                .description("Documentación de la API backend de SIGCON")
                                                .contact(new Contact().name("SIGCON Team")
                                                                .email("noreply@sigcon.local"))
                                                .license(new License().name("Apache 2.0").url("http://springdoc.org")))
                                // 1. Aquí definimos los "Módulos" (Tags globales) en el orden que queremos que
                                // aparezcan.
                                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                                                .name("Módulo de Parametrización"))
                                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                                                .name("Módulo de Listas Contables"))
                                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                                                .name("Módulo de Terceros"))
                                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                                                .name("Módulo de Activos"))
                                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                                                .name("Módulo de Reportes"));
        }
}
