package com.vocawik.module.logging.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.logging.http.ClientIpResolver;
import com.vocawik.module.logging.http.LoggingAspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingPropertiesConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            LoggingPropertiesConfiguration.class,
                            ClientIpResolver.class,
                            LoggingAspect.class);

    @Test
    @DisplayName("Should register HTTP logging beans when enabled")
    void httpLoggingEnabled_shouldRegisterBeans() {
        contextRunner
                .withPropertyValues(
                        "vocawik.logging.http.enabled=true",
                        "vocawik.logging.http.trusted-proxy-cidrs=127.0.0.1/32")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(LoggingHttpProperties.class);
                            assertThat(context).hasSingleBean(ClientIpResolver.class);
                            assertThat(context).hasSingleBean(LoggingAspect.class);
                            assertThat(
                                            context.getBean(LoggingHttpProperties.class)
                                                    .trustedProxyCidrs())
                                    .isEqualTo("127.0.0.1/32");
                        });
    }

    @Test
    @DisplayName("Should not register HTTP logging beans when disabled")
    void httpLoggingDisabled_shouldNotRegisterBeans() {
        contextRunner
                .withPropertyValues("vocawik.logging.http.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(LoggingHttpProperties.class);
                            assertThat(context).doesNotHaveBean(ClientIpResolver.class);
                            assertThat(context).doesNotHaveBean(LoggingAspect.class);
                        });
    }

    @Test
    @DisplayName("Should not register HTTP logging beans when property is missing")
    void httpLoggingMissingProperty_shouldNotRegisterBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(LoggingHttpProperties.class);
                    assertThat(context).doesNotHaveBean(ClientIpResolver.class);
                    assertThat(context).doesNotHaveBean(LoggingAspect.class);
                });
    }
}
