package com.vocawik.module.logging.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.vocawik.module.web.clientip.ClientIpResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingAspectConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(ClientIpResolver.class, () -> mock(ClientIpResolver.class))
                    .withUserConfiguration(LoggingAspect.class);

    @Test
    @DisplayName("Should register HTTP logging aspect when enabled")
    void httpLoggingEnabled_shouldRegisterAspect() {
        contextRunner
                .withPropertyValues("logging.http.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(LoggingAspect.class));
    }

    @Test
    @DisplayName("Should not register HTTP logging aspect when disabled")
    void httpLoggingDisabled_shouldNotRegisterAspect() {
        contextRunner
                .withPropertyValues("logging.http.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LoggingAspect.class));
    }

    @Test
    @DisplayName("Should not register HTTP logging aspect when property is missing")
    void httpLoggingMissingProperty_shouldNotRegisterAspect() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(LoggingAspect.class));
    }
}
