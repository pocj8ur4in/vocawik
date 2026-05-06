package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.http.SecurityHttpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

class SecurityHttpAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    WebMvcAutoConfiguration.class,
                                    SecurityErrorAutoConfiguration.class,
                                    SecurityHttpAutoConfiguration.class,
                                    SecurityAutoConfiguration.class));

    @Test
    @DisplayName("Should register the default security filter chain automatically")
    void autoConfiguration_shouldRegisterDefaultSecurityFilterChain() {
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

    @Test
    @DisplayName("Should reject disabling the default chain without a consumer chain")
    void autoConfiguration_whenDefaultChainDisabledWithoutConsumerChain_shouldFailStartup() {
        contextRunner
                .withPropertyValues("security.http.default-chain-enabled=false")
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessageContaining(
                                                "security.http.default-chain-enabled=false"));
    }

    @Test
    @DisplayName("Should allow a consumer owned chain when the default chain is disabled")
    void autoConfiguration_whenDefaultChainDisabledWithConsumerChain_shouldStart() {
        contextRunner
                .withUserConfiguration(ConsumerSecurityChainConfiguration.class)
                .withPropertyValues("security.http.default-chain-enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBeansOfType(SecurityFilterChain.class))
                                    .containsOnlyKeys("consumerSecurityFilterChain");
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerSecurityChainConfiguration {

        @Bean
        @Order(1)
        SecurityFilterChain consumerSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }
    }
}
