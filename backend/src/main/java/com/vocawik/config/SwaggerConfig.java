package com.vocawik.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
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

    private static final String ACCEPT_LANGUAGE_HEADER = "Accept-Language";
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

    /** Adds the request locale header to every operation in Swagger UI. */
    @Bean
    public OperationCustomizer acceptLanguageHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            if (hasHeaderParameter(operation, ACCEPT_LANGUAGE_HEADER)) {
                return operation;
            }

            Parameter parameter =
                    new Parameter()
                            .in("header")
                            .name(ACCEPT_LANGUAGE_HEADER)
                            .required(false)
                            .description(
                                    "Optional locale hint for localized responses. "
                                            + "Supported values: ko, en, ja, zh. ")
                            .schema(
                                    new StringSchema()
                                            ._default("en")
                                            ._enum(List.of("ko", "en", "ja", "zh")));

            if (operation.getParameters() == null) {
                operation.setParameters(new ArrayList<>());
            }
            operation.getParameters().add(parameter);
            return operation;
        };
    }

    private boolean hasHeaderParameter(Operation operation, String headerName) {
        if (operation.getParameters() == null) {
            return false;
        }
        return operation.getParameters().stream()
                .anyMatch(
                        parameter ->
                                "header".equals(parameter.getIn())
                                        && headerName.equalsIgnoreCase(parameter.getName()));
    }
}
