package com.vocawik.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger configuration.
 *
 * <p>Sets API metadata and JWT Bearer authentication scheme for the Swagger UI.
 */
@Configuration
public class SwaggerConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${spring.application.description}")
    private String applicationDescription;

    @Value("${spring.application.version}")
    private String applicationVersion;

    /**
     * Configures the OpenAPI specification with project info and JWT security.
     *
     * @return the configured {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title(applicationName)
                                .description(applicationDescription)
                                .version(applicationVersion))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        new SecurityScheme()
                                                .name(SECURITY_SCHEME_NAME)
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")));
    }
}
