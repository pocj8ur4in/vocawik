package com.vocawik.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(null, null, null, null);
    }

    @Test
    @DisplayName("PasswordEncoder should be BCryptPasswordEncoder")
    void passwordEncoder_shouldReturnBCrypt() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
    }

    @Test
    @DisplayName("PasswordEncoder should encode and verify passwords correctly")
    void passwordEncoder_shouldEncodeAndMatch() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "testPassword123";

        String encoded = encoder.encode(raw);

        assertThat(encoded).isNotEqualTo(raw);
        assertThat(encoder.matches(raw, encoded)).isTrue();
        assertThat(encoder.matches("wrongPassword", encoded)).isFalse();
    }

    @Test
    @DisplayName("PasswordEncoder should produce different hashes for same input")
    void passwordEncoder_shouldProduceDifferentHashes() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "samePassword";

        String encoded1 = encoder.encode(raw);
        String encoded2 = encoder.encode(raw);

        assertThat(encoded1).isNotEqualTo(encoded2);
        assertThat(encoder.matches(raw, encoded1)).isTrue();
        assertThat(encoder.matches(raw, encoded2)).isTrue();
    }
}
