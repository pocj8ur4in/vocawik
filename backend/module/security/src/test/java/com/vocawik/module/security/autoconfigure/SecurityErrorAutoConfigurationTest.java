package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.module.security.error.ApiAccessDeniedHandler;
import com.vocawik.module.security.error.ApiAuthenticationEntryPoint;
import com.vocawik.module.security.error.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

class SecurityErrorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(SecurityErrorAutoConfiguration.class))
                    .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    @DisplayName("Should discover JSON security error handlers automatically")
    void autoConfiguration_withServletAndJacksonClasspath_shouldRegisterHandlers() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(SecurityErrorResponseWriter.class);
                    assertThat(context).hasSingleBean(ApiAuthenticationEntryPoint.class);
                    assertThat(context).hasSingleBean(ApiAccessDeniedHandler.class);
                    assertThat(context).hasSingleBean(AuthenticationEntryPoint.class);
                    assertThat(context).hasSingleBean(AccessDeniedHandler.class);
                });
    }

    @Test
    @DisplayName("Should preserve consumer security error handlers")
    void autoConfiguration_withConsumerBeans_shouldBackOff() {
        SecurityErrorResponseWriter writer =
                new SecurityErrorResponseWriter(new ObjectMapper());
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(writer);
        ApiAccessDeniedHandler accessDeniedHandler = new ApiAccessDeniedHandler(writer);

        contextRunner
                .withBean(SecurityErrorResponseWriter.class, () -> writer)
                .withBean(ApiAuthenticationEntryPoint.class, () -> entryPoint)
                .withBean(ApiAccessDeniedHandler.class, () -> accessDeniedHandler)
                .run(
                        context -> {
                            assertThat(context.getBean(SecurityErrorResponseWriter.class))
                                    .isSameAs(writer);
                            assertThat(context.getBean(ApiAuthenticationEntryPoint.class))
                                    .isSameAs(entryPoint);
                            assertThat(context.getBean(ApiAccessDeniedHandler.class))
                                    .isSameAs(accessDeniedHandler);
                        });
    }

    @Test
    @DisplayName("Should skip security error handlers without servlet support")
    void autoConfiguration_withoutServletClasspath_shouldRemainInactive() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(HttpServletResponse.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(SecurityErrorResponseWriter.class);
                            assertThat(context).doesNotHaveBean(ApiAuthenticationEntryPoint.class);
                            assertThat(context).doesNotHaveBean(ApiAccessDeniedHandler.class);
                        });
    }
}
