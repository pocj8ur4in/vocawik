package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.guest.CurrentGuestArgumentResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class SecurityCurrentGuestAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    WebMvcAutoConfiguration.class,
                                    SecurityCurrentGuestAutoConfiguration.class));

    @Test
    @DisplayName("Should register the current guest resolver automatically")
    void autoConfiguration_shouldRegisterCurrentGuestResolver() {
        contextRunner.run(
                context -> {
                    CurrentGuestArgumentResolver resolver =
                            context.getBean(CurrentGuestArgumentResolver.class);

                    assertThat(context.getBean(RequestMappingHandlerAdapter.class)
                                    .getArgumentResolvers())
                            .contains(resolver);
                });
    }
}
