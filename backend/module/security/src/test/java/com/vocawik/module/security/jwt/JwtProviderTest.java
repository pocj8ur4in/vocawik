package com.vocawik.module.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";
    private static final String DIFFERENT_SECRET =
            "ZGlmZmVyZW50LXNlY3JldC1rZXktZm9yLXRlc3RpbmctcHVycG9zZXMtb25seS1tdXN0LWJlLWF0LWxlYXN0LTI1Ni1iaXRz";

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = jwtProvider(SECRET, "vocawik", "vocawik-api", Duration.ofHours(1));
    }

    @Test
    @DisplayName("Should generate access token and extract subject")
    void generateAccessToken_shouldReturnValidToken() {
        String subject = UUID.randomUUID().toString();
        String token = jwtProvider.generateAccessToken(subject);

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.getSubject(token)).isEqualTo(subject);
        assertThat(jwtProvider.getRole(token)).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should generate refresh token and extract subject")
    void generateRefreshToken_shouldReturnValidToken() {
        String subject = UUID.randomUUID().toString();
        String token = jwtProvider.generateRefreshToken(subject);

        assertThat(token).isNotBlank();
        assertThat(jwtProvider.getSubject(token)).isEqualTo(subject);
        assertThat(jwtProvider.validateRefreshToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should include refresh token metadata")
    void generateRefreshToken_withMetadata_shouldContainJtiAndFamily() {
        String subject = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();

        String token = jwtProvider.generateRefreshToken(subject, "USER", familyId, tokenId);

        assertThat(jwtProvider.getTokenId(token)).isEqualTo(tokenId);
        assertThat(jwtProvider.getRefreshFamily(token)).isEqualTo(familyId);
    }

    @Test
    @DisplayName("Should validate valid access token")
    void validateAccessToken_withValidToken_shouldReturnTrue() {
        UUID subject = UUID.randomUUID();
        String token = jwtProvider.generateAccessToken(subject.toString());

        assertThat(jwtProvider.validateAccessToken(token)).isTrue();
        assertThat(jwtProvider.parseAccessToken(token))
                .contains(new AuthPrincipal(subject, "USER"));
    }

    @Test
    @DisplayName("Should reject access token without subject")
    void validateAccessToken_withoutSubject_shouldReturnFalse() {
        String token =
                JwtTestTokens.signedToken(
                        SECRET, builder -> builder.claim("role", "USER").claim("typ", "ACCESS"));

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject access token with malformed role")
    void validateAccessToken_withMalformedRole_shouldReturnFalse() {
        String numericRoleToken =
                JwtTestTokens.signedToken(
                        SECRET,
                        builder ->
                                builder.subject(UUID.randomUUID().toString())
                                        .claim("role", 123)
                                        .claim("typ", "ACCESS"));
        String blankRoleToken =
                JwtTestTokens.signedToken(
                        SECRET,
                        builder ->
                                builder.subject(UUID.randomUUID().toString())
                                        .claim("role", " ")
                                        .claim("typ", "ACCESS"));

        assertThat(jwtProvider.validateAccessToken(numericRoleToken)).isFalse();
        assertThat(jwtProvider.validateAccessToken(blankRoleToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject access token without role")
    void validateAccessToken_withoutRole_shouldReturnFalse() {
        String token =
                JwtTestTokens.signedToken(
                        SECRET,
                        builder ->
                                builder.subject(UUID.randomUUID().toString())
                                        .claim("typ", "ACCESS"));

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired access token")
    void validateAccessToken_withExpiredToken_shouldReturnFalse() {
        JwtProvider shortLived =
                jwtProvider(SECRET, "vocawik", "vocawik-api", Duration.ofMillis(-1000));
        String token = shortLived.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid token")
    void validateAccessToken_withInvalidToken_shouldReturnFalse() {
        assertThat(jwtProvider.validateAccessToken("invalid.token.value")).isFalse();
    }

    @Test
    @DisplayName("Should reject token signed with different secret")
    void validateAccessToken_withDifferentSecret_shouldReturnFalse() {
        JwtProvider otherProvider =
                jwtProvider(DIFFERENT_SECRET, "vocawik", "vocawik-api", Duration.ofHours(1));
        String token = otherProvider.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject refresh token as access token")
    void validateAccessToken_withRefreshToken_shouldReturnFalse() {
        String refreshToken = jwtProvider.generateRefreshToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(refreshToken)).isFalse();
        assertThat(jwtProvider.validateRefreshToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("Should reject token from different issuer")
    void validateAccessToken_withDifferentIssuer_shouldReturnFalse() {
        JwtProvider otherProvider =
                jwtProvider(SECRET, "other-issuer", "vocawik-api", Duration.ofHours(1));
        String token = otherProvider.generateAccessToken(UUID.randomUUID().toString());

        assertThat(jwtProvider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Should reject refresh token without rotation metadata")
    void validateRefreshToken_withoutMetadata_shouldReturnFalse() {
        String missingMetadataToken =
                JwtTestTokens.signedToken(
                        SECRET,
                        builder ->
                                builder.subject(UUID.randomUUID().toString())
                                        .claim("role", "USER")
                                        .claim("typ", "REFRESH"));
        String missingFamilyToken =
                JwtTestTokens.signedToken(
                        SECRET,
                        builder ->
                                builder.subject(UUID.randomUUID().toString())
                                        .claim("role", "USER")
                                        .claim("typ", "REFRESH")
                                        .id(UUID.randomUUID().toString()));
        String malformedFamilyToken =
                JwtTestTokens.signedToken(
                        SECRET,
                        builder ->
                                builder.subject(UUID.randomUUID().toString())
                                        .claim("role", "USER")
                                        .claim("typ", "REFRESH")
                                        .id(UUID.randomUUID().toString())
                                        .claim("fam", 123));

        assertThat(jwtProvider.validateRefreshToken(missingMetadataToken)).isFalse();
        assertThat(jwtProvider.validateRefreshToken(missingFamilyToken)).isFalse();
        assertThat(jwtProvider.validateRefreshToken(malformedFamilyToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid claims when generating tokens")
    void generateToken_withInvalidClaims_shouldThrowException() {
        String subject = UUID.randomUUID().toString();

        assertThatThrownBy(() -> jwtProvider.generateAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtProvider.generateAccessToken("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtProvider.generateAccessToken(subject, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtProvider.generateRefreshToken(subject, "USER", "", "token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jwtProvider.generateRefreshToken(subject, "USER", "family", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should expose token lifetimes in seconds")
    void expirationSeconds_shouldReturnConfiguredLifetimes() {
        JwtProvider provider =
                new JwtProvider(
                        new JwtProperties(
                                SECRET,
                                "vocawik",
                                "vocawik-api",
                                Duration.ofMinutes(30),
                                Duration.ofDays(14)));

        assertThat(provider.getAccessExpirationSeconds()).isEqualTo(1800);
        assertThat(provider.getRefreshExpirationSeconds()).isEqualTo(1_209_600);
    }

    private JwtProvider jwtProvider(
            String secret, String issuer, String audience, Duration accessExpiration) {
        return new JwtProvider(
                new JwtProperties(secret, issuer, audience, accessExpiration, Duration.ofDays(30)));
    }
}
