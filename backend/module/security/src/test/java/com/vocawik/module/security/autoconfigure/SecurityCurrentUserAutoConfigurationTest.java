package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.user.CurrentUserArgumentResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

class SecurityCurrentUserAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    WebMvcAutoConfiguration.class,
                                    SecurityCurrentUserAutoConfiguration.class));

    @Test
    @DisplayName("Should register the current user resolver automatically")
    void autoConfiguration_shouldRegisterCurrentUserResolver() {
        contextRunner.run(
                context -> {
                    CurrentUserArgumentResolver resolver =
                            context.getBean(CurrentUserArgumentResolver.class);

                    assertThat(context.getBean(RequestMappingHandlerAdapter.class)
                                    .getArgumentResolvers())
                            .contains(resolver);
                });
    }
}
