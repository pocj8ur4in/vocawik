package com.vocawik.module.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityPasswordEncoderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(SecurityPasswordEncoderAutoConfiguration.class));

    @Test
    @DisplayName("Should discover the default BCrypt password encoder")
    void autoConfiguration_withSecurityClasspath_shouldRegisterEncoder() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(PasswordEncoder.class);
                    assertThat(context).hasSingleBean(BCryptPasswordEncoder.class);
                });
    }

    @Test
    @DisplayName("Should preserve a consumer password encoder")
    void autoConfiguration_withConsumerEncoder_shouldBackOff() {
        PasswordEncoder consumerEncoder = new BCryptPasswordEncoder();

        contextRunner
                .withBean(PasswordEncoder.class, () -> consumerEncoder)
                .run(
                        context ->
                                assertThat(context.getBean(PasswordEncoder.class))
                                        .isSameAs(consumerEncoder));
    }

    @Test
    @DisplayName("Should skip password encoding without its API")
    void autoConfiguration_withoutPasswordEncoderApi_shouldRemainInactive() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(PasswordEncoder.class))
                .run(context -> assertThat(context).doesNotHaveBean(PasswordEncoder.class));
    }

    @Test
    @DisplayName("Should encode and verify passwords")
    void autoConfiguration_shouldEncodeAndVerifyPassword() {
        contextRunner.run(
                context -> {
                    PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
                    String encodedPassword = encoder.encode("Password123!");

                    assertThat(encodedPassword).isNotEqualTo("Password123!");
                    assertThat(encoder.matches("Password123!", encodedPassword)).isTrue();
                    assertThat(encoder.matches("WrongPassword123!", encodedPassword)).isFalse();
                });
    }
}
