package com.vocawik.module.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.autoconfigure.SecurityJwtAutoConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JwtPropertiesTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SecurityJwtAutoConfiguration.class));

    @Test
    @DisplayName("Should bind JWT properties")
    void jwtProperties_shouldBind() {
        contextRunner
                .withPropertyValues(
                        "security.jwt.secret=" + SECRET,
                        "security.jwt.issuer=vocawik",
                        "security.jwt.audience=vocawik-api",
                        "security.jwt.access-expiration=15m",
                        "security.jwt.refresh-expiration=7d")
                .run(
                        context -> {
                            JwtProperties properties = context.getBean(JwtProperties.class);

                            assertThat(properties.secret()).isEqualTo(SECRET);
                            assertThat(properties.issuer()).isEqualTo("vocawik");
                            assertThat(properties.audience()).isEqualTo("vocawik-api");
                            assertThat(properties.accessExpiration())
                                    .isEqualTo(Duration.ofMinutes(15));
                            assertThat(properties.refreshExpiration())
                                    .isEqualTo(Duration.ofDays(7));
                        });
    }

    @Test
    @DisplayName("Should fail when JWT secret is blank")
    void jwtProperties_withBlankSecret_shouldFail() {
        contextRunner
                .withPropertyValues("security.jwt.secret=")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }
}
