package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.vocawik.module.security.guest.GuestAuthenticationFilter;
import com.vocawik.module.security.guest.GuestAuthenticationProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class SecurityGuestAuthenticationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(SecurityGuestAuthenticationAutoConfiguration.class));

    @Test
    @DisplayName("Should not register guest authentication without provider and MVC mapping")
    void autoConfiguration_withoutDependencies_shouldNotRegisterGuestAuthentication() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(GuestAuthenticationFilter.class);
                    assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
                });
    }

    @Test
    @DisplayName("Should register guest authentication when provider and MVC mapping are available")
    void autoConfiguration_withDependencies_shouldRegisterGuestAuthentication() {
        contextRunner
                .withBean(GuestAuthenticationProvider.class, () -> mock(GuestAuthenticationProvider.class))
                .withBean(
                        RequestMappingHandlerMapping.class,
                        () -> mock(RequestMappingHandlerMapping.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(GuestAuthenticationFilter.class);
                            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                            assertThat(
                                            context.getBean(
                                                    "guestAuthenticationFilterRegistration",
                                                    FilterRegistrationBean.class))
                                    .extracting(FilterRegistrationBean::isEnabled)
                                    .isEqualTo(false);
                        });
    }
}
