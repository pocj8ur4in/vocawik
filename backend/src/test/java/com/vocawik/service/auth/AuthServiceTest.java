package com.vocawik.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocawik.repository.user.UserAuthProviderRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.jwt.JwtProvider;
import com.vocawik.web.exception.UnauthorizedException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AuthServiceTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";
    private static final String ISSUER = "vocawik";
    private static final String AUDIENCE = "vocawik-api";
    private static final long ACCESS_EXPIRATION = 3_600_000L;
    private static final long REFRESH_EXPIRATION = 86_400_000L;

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private JwtProvider jwtProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        jwtProvider =
                new JwtProvider(SECRET, ISSUER, AUDIENCE, ACCESS_EXPIRATION, REFRESH_EXPIRATION);

        authService =
                new AuthService(
                        mock(GoogleOAuthClient.class),
                        mock(OAuthProperties.class),
                        mock(UserRepository.class),
                        mock(UserAuthProviderRepository.class),
                        jwtProvider,
                        stringRedisTemplate);
    }

    @Test
    @DisplayName("Refresh should rotate token when token is first used")
    void refresh_withFreshToken_shouldRotateTokens() {
        String subject = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.generateRefreshToken(subject, "USER", familyId, tokenId);
        Duration ttl = Duration.ofSeconds(jwtProvider.getRefreshExpirationSeconds());

        when(stringRedisTemplate.hasKey("auth:refresh:family:revoked:" + familyId))
                .thenReturn(false);
        when(valueOperations.setIfAbsent("auth:refresh:used:" + tokenId, "1", ttl))
                .thenReturn(true);

        AuthTokenBundle result = authService.refresh(refreshToken);

        assertThat(jwtProvider.validateAccessToken(result.accessToken())).isTrue();
        assertThat(jwtProvider.validateRefreshToken(result.refreshToken())).isTrue();
        assertThat(jwtProvider.getSubject(result.accessToken())).isEqualTo(subject);
        assertThat(jwtProvider.getRefreshFamily(result.refreshToken())).isEqualTo(familyId);
        assertThat(jwtProvider.getTokenId(result.refreshToken())).isNotEqualTo(tokenId);
    }

    @Test
    @DisplayName("Refresh token reuse should revoke family and throw unauthorized")
    void refresh_withReusedToken_shouldRevokeFamily() {
        String subject = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.generateRefreshToken(subject, "USER", familyId, tokenId);
        Duration ttl = Duration.ofSeconds(jwtProvider.getRefreshExpirationSeconds());

        when(stringRedisTemplate.hasKey("auth:refresh:family:revoked:" + familyId))
                .thenReturn(false);
        when(valueOperations.setIfAbsent("auth:refresh:used:" + tokenId, "1", ttl))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("reuse detected");

        verify(valueOperations).set("auth:refresh:family:revoked:" + familyId, "1", ttl);
    }

    @Test
    @DisplayName("Revoked refresh family should fail before reuse check")
    void refresh_withRevokedFamily_shouldThrowUnauthorized() {
        String subject = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtProvider.generateRefreshToken(subject, "USER", familyId, tokenId);

        when(stringRedisTemplate.hasKey("auth:refresh:family:revoked:" + familyId))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.refresh(refreshToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("family is revoked");

        verify(valueOperations, never()).setIfAbsent(any(), eq("1"), any(Duration.class));
    }
}
