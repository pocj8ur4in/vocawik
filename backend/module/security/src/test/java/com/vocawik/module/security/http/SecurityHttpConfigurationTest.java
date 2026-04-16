package com.vocawik.module.security.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.error.ApiAccessDeniedHandler;
import com.vocawik.module.security.error.ApiAuthenticationEntryPoint;
import com.vocawik.module.security.error.SecurityErrorResponseWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

class SecurityHttpConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    SecurityAutoConfiguration.class,
                                    WebMvcAutoConfiguration.class))
                    .withUserConfiguration(
                            SecurityHttpConfiguration.class,
                            ApiAuthenticationEntryPoint.class,
                            ApiAccessDeniedHandler.class,
                            SecurityErrorResponseWriter.class);

    @Test
    @DisplayName("Should register security filter chain")
    void securityFilterChain_shouldRegister() {
        contextRunner
                .withPropertyValues("security.http.allows=/api/v1/status")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(SecurityHttpProperties.class);
                            assertThat(context).hasSingleBean(SecurityFilterChain.class);
                            assertThat(context.getBean(SecurityHttpProperties.class).allows())
                                    .containsExactly("/api/v1/status");
                        });
    }
}
