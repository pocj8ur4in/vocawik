package com.vocawik.module.security.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SecurityHttpPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class);

    @Test
    @DisplayName("Should use empty allows by default")
    void securityHttpProperties_shouldUseDefaultAllows() {
        contextRunner.run(
                context ->
                        assertThat(context.getBean(SecurityHttpProperties.class).allows())
                                .isEmpty());
    }

    @Test
    @DisplayName("Should bind allows")
    void securityHttpProperties_shouldBindAllows() {
        contextRunner
                .withPropertyValues(
                        "security.http.allows[0]=/api/v1/status",
                        "security.http.allows[1]=/actuator/health")
                .run(
                        context ->
                                assertThat(context.getBean(SecurityHttpProperties.class).allows())
                                        .containsExactly("/api/v1/status", "/actuator/health"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityHttpProperties.class)
    private static class TestConfiguration {}
}
