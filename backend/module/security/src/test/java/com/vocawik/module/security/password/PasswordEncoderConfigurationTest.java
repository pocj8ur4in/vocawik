package com.vocawik.module.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigurationTest {

    private final PasswordEncoderConfiguration configuration = new PasswordEncoderConfiguration();

    @Test
    @DisplayName("Should provide BCrypt password encoder")
    void passwordEncoder_shouldReturnBCrypt() {
        PasswordEncoder encoder = configuration.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("Should encode and verify password")
    void passwordEncoder_shouldEncodeAndMatch() {
        PasswordEncoder encoder = configuration.passwordEncoder();
        String rawPassword = "Password123!";

        String encodedPassword = encoder.encode(rawPassword);

        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(encoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(encoder.matches("WrongPassword123!", encodedPassword)).isFalse();
    }

    @Test
    @DisplayName("Should produce different hashes for the same password")
    void passwordEncoder_shouldProduceDifferentHashes() {
        PasswordEncoder encoder = configuration.passwordEncoder();
        String rawPassword = "Password123!";

        String firstHash = encoder.encode(rawPassword);
        String secondHash = encoder.encode(rawPassword);

        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(encoder.matches(rawPassword, firstHash)).isTrue();
        assertThat(encoder.matches(rawPassword, secondHash)).isTrue();
    }
}
