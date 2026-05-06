package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.jwt.JwtAuthenticationFilter;
import com.vocawik.module.security.jwt.JwtProperties;
import com.vocawik.module.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

class SecurityJwtAutoConfigurationTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SecurityJwtAutoConfiguration.class));

    @Test
    @DisplayName("Should not register JWT beans without secret")
    void autoConfiguration_withoutSecret_shouldNotRegisterJwtBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(JwtProperties.class);
                    assertThat(context).doesNotHaveBean(JwtProvider.class);
                    assertThat(context).doesNotHaveBean(JwtAuthenticationFilter.class);
                });
    }

    @Test
    @DisplayName("Should register JWT beans with secret")
    void autoConfiguration_withSecret_shouldRegisterJwtBeans() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=" + SECRET,
                        "security.jwt.issuer=vocawik",
                        "security.jwt.audience=vocawik-api",
                        "security.jwt.access-expiration=1h",
                        "security.jwt.refresh-expiration=30d")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(JwtProperties.class);
                            assertThat(context).hasSingleBean(JwtProvider.class);
                            assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
                            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                            assertThat(
                                            context.getBean(
                                                    "jwtAuthenticationFilterRegistration",
                                                    FilterRegistrationBean.class))
                                    .extracting(FilterRegistrationBean::isEnabled)
                                    .isEqualTo(false);
                        });
    }
}
