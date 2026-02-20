package com.vocawik.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.security.jwt.JwtProvider;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";
    private static final String ISSUER = "vocawik";
    private static final String AUDIENCE = "vocawik-api";
    private static final long ACCESS_EXPIRATION = 3_600_000L;
    private static final long REFRESH_EXPIRATION = 86_400_000L;

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider =
                new JwtProvider(SECRET, ISSUER, AUDIENCE, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
    }

    @Test
    @DisplayName("Generate access token and extract subject")
    void generateAccessToken_shouldReturnValidToken() {
        String subject = UUID.randomUUID().toString();
        String token = jwtProvider.generateAccessToken(subject);

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.getSubject(token)).isEqualTo(subject);
    }

    @Test
    @DisplayName("Generate refresh token and extract subject")
    void generateRefreshToken_shouldReturnValidToken() {
        String subject = UUID.randomUUID().toString();
        String token = jwtProvider.generateRefreshToken(subject);

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.getSubject(token)).isEqualTo(subject);
    }

    @Test
    @DisplayName("Validate valid token")
    void validateToken_withValidToken_shouldReturnTrue() {
        String token = jwtProvider.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isTrue();
    }

    @Test
    @DisplayName("Validate expired token")
    void validateToken_withExpiredToken_shouldReturnFalse() {
        JwtProvider shortLived = new JwtProvider(SECRET, ISSUER, AUDIENCE, -1000L, -1000L);
        String token = shortLived.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Validate invalid token")
    void validateToken_withInvalidToken_shouldReturnFalse() {
        assertThat(jwtProvider.validateAccessToken("invalid.token.value")).isFalse();
    }

    @Test
    @DisplayName("Validate token signed with different secret")
    void validateToken_withDifferentSecret_shouldReturnFalse() {
        String differentSecret =
                "ZGlmZmVyZW50LXNlY3JldC1rZXktZm9yLXRlc3RpbmctcHVycG9zZXMtb25seS1tdXN0LWJlLWF0LWxlYXN0LTI1Ni1iaXRz";
        JwtProvider otherProvider =
                new JwtProvider(
                        differentSecret, ISSUER, AUDIENCE, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
        String token = otherProvider.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Refresh token should not pass access token validation")
    void validateAccessToken_withRefreshToken_shouldReturnFalse() {
        String refreshToken = jwtProvider.generateRefreshToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(refreshToken)).isFalse();
        assertThat(jwtProvider.validateRefreshToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("Token from different issuer should fail validation")
    void validateToken_withDifferentIssuer_shouldReturnFalse() {
        JwtProvider otherProvider =
                new JwtProvider(
                        SECRET, "other-issuer", AUDIENCE, ACCESS_EXPIRATION, REFRESH_EXPIRATION);
        String token = otherProvider.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }
}
