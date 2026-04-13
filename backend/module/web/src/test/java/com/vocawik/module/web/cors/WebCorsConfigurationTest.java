package com.vocawik.module.web.cors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class WebCorsConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(WebCorsConfiguration.class);

    @Test
    @DisplayName("Should register CORS configuration source with defaults")
    void corsConfigurationSource_shouldRegisterWithDefaults() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(WebCorsProperties.class);
                    assertThat(context).hasSingleBean(CorsConfigurationSource.class);

                    CorsConfiguration configuration =
                            corsConfiguration(
                                    context.getBean(CorsConfigurationSource.class), "/api/status");

                    assertThat(configuration.getAllowedOrigins()).isEmpty();
                    assertThat(configuration.getAllowedMethods())
                            .containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
                    assertThat(configuration.getAllowedHeaders()).containsExactly("*");
                    assertThat(configuration.getExposedHeaders())
                            .containsExactly("X-Request-Id", "X-Client-IP");
                    assertThat(configuration.getAllowCredentials()).isTrue();
                    assertThat(configuration.getMaxAge()).isEqualTo(3600L);
                });
    }

    @Test
    @DisplayName("Should bind CORS properties")
    void corsProperties_shouldBind() {
        contextRunner
                .withPropertyValues(
                        "web.cors.allowed-origins=https://vocawik.com",
                        "web.cors.allowed-methods=GET,POST",
                        "web.cors.allowed-headers=Authorization,Content-Type",
                        "web.cors.exposed-headers=X-Request-Id",
                        "web.cors.allow-credentials=false",
                        "web.cors.max-age=600",
                        "web.cors.path-pattern=/api/**")
                .run(
                        context -> {
                            CorsConfiguration configuration =
                                    corsConfiguration(
                                            context.getBean(CorsConfigurationSource.class),
                                            "/api/status");

                            assertThat(configuration.getAllowedOrigins())
                                    .containsExactly("https://vocawik.com");
                            assertThat(configuration.getAllowedMethods())
                                    .containsExactly("GET", "POST");
                            assertThat(configuration.getAllowedHeaders())
                                    .containsExactly("Authorization", "Content-Type");
                            assertThat(configuration.getExposedHeaders())
                                    .containsExactly("X-Request-Id");
                            assertThat(configuration.getAllowCredentials()).isFalse();
                            assertThat(configuration.getMaxAge()).isEqualTo(600L);
                        });
    }

    @Test
    @DisplayName("Should reject wildcard origins when credentials are enabled")
    void corsProperties_shouldRejectWildcardOriginsWithCredentials() {
        contextRunner
                .withPropertyValues("web.cors.allowed-origins=*", "web.cors.allow-credentials=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should allow wildcard origins when credentials are disabled")
    void corsProperties_shouldAllowWildcardOriginsWithoutCredentials() {
        contextRunner
                .withPropertyValues(
                        "web.cors.allowed-origins=*", "web.cors.allow-credentials=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();

                            CorsConfiguration configuration =
                                    corsConfiguration(
                                            context.getBean(CorsConfigurationSource.class),
                                            "/api/status");

                            assertThat(configuration.getAllowedOrigins()).containsExactly("*");
                            assertThat(configuration.getAllowCredentials()).isFalse();
                        });
    }

    private CorsConfiguration corsConfiguration(CorsConfigurationSource source, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertThat(configuration).isNotNull();
        return configuration;
    }
}
