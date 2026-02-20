package com.vocawik.service.auth;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserAuthProvider;
import com.vocawik.repository.user.UserAuthProviderRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.jwt.JwtProvider;
import com.vocawik.web.exception.UnauthorizedException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authentication service for OAuth login and token issuance. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "StringRedisTemplate is a Spring-managed infrastructure bean and is not exposed externally.")
public class AuthService {

    private static final String REFRESH_USED_KEY_PREFIX = "auth:refresh:used:";
    private static final String REFRESH_REVOKED_FAMILY_KEY_PREFIX = "auth:refresh:family:revoked:";

    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthProperties oAuthProperties;
    private final UserRepository userRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Builds Google OAuth authorize URL.
     *
     * @return provider authorize URL
     */
    public String buildGoogleAuthorizeUrl(String state) {
        return oAuthProperties.getAuthUri()
                + "?response_type=code"
                + "&client_id="
                + encode(oAuthProperties.getClientId())
                + "&redirect_uri="
                + encode(oAuthProperties.getRedirectUri())
                + "&scope="
                + encode("openid email profile")
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state="
                + encode(state);
    }

    /**
     * Handles Google OAuth callback and returns issued token bundle.
     *
     * @param code authorization code
     * @return issued tokens
     */
    @Transactional
    public AuthTokenBundle authenticateGoogle(String code) {
        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeCode(code);
        GoogleUserInfo userInfo = googleOAuthClient.fetchUserInfo(tokenResponse.accessToken());

        User user =
                userAuthProviderRepository
                        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, userInfo.sub())
                        .map(UserAuthProvider::getUser)
                        .orElseGet(() -> linkOrCreateGoogleUser(userInfo));

        user.touchLastLoginAt();

        String role = user.getRole().name();
        String accessToken = jwtProvider.generateAccessToken(user.getUuid(), role);
        String familyId = UUID.randomUUID().toString();
        String refreshToken =
                jwtProvider.generateRefreshToken(
                        user.getUuid(), role, familyId, UUID.randomUUID().toString());

        return new AuthTokenBundle(
                accessToken, refreshToken, jwtProvider.getAccessExpirationSeconds());
    }

    /**
     * Reissues token bundle from valid refresh token.
     *
     * @param refreshToken refresh token
     * @return reissued token bundle
     */
    public AuthTokenBundle refresh(String refreshToken) {
        if (refreshToken == null || !jwtProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or missing refresh token.");
        }

        String subject = jwtProvider.getSubject(refreshToken);
        String role = jwtProvider.getRole(refreshToken);
        String familyId = resolveRefreshFamily(refreshToken, subject);
        String tokenId = resolveRefreshTokenId(refreshToken);
        Duration refreshTtl = Duration.ofSeconds(jwtProvider.getRefreshExpirationSeconds());

        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(refreshFamilyRevokedKey(familyId)))) {
            throw new UnauthorizedException(
                    "Refresh token family is revoked. Please sign in again.");
        }

        boolean firstUse =
                Boolean.TRUE.equals(
                        stringRedisTemplate
                                .opsForValue()
                                .setIfAbsent(REFRESH_USED_KEY_PREFIX + tokenId, "1", refreshTtl));
        if (!firstUse) {
            stringRedisTemplate
                    .opsForValue()
                    .set(refreshFamilyRevokedKey(familyId), "1", refreshTtl);
            throw new UnauthorizedException("Refresh token reuse detected. Please sign in again.");
        }

        String accessToken = jwtProvider.generateAccessToken(subject, role);
        String nextRefreshToken =
                jwtProvider.generateRefreshToken(
                        subject, role, familyId, UUID.randomUUID().toString());

        return new AuthTokenBundle(
                accessToken, nextRefreshToken, jwtProvider.getAccessExpirationSeconds());
    }

    /**
     * Returns refresh token expiration in seconds.
     *
     * @return refresh token expiration (seconds)
     */
    public long getRefreshExpirationSeconds() {
        return jwtProvider.getRefreshExpirationSeconds();
    }

    private User linkOrCreateGoogleUser(GoogleUserInfo userInfo) {
        User user =
                userRepository
                        .findByEmail(userInfo.email())
                        .orElseGet(
                                () ->
                                        userRepository.save(
                                                User.create(
                                                        userInfo.email(),
                                                        resolveNickname(
                                                                userInfo.name(),
                                                                userInfo.email()))));

        UserAuthProvider mapping =
                UserAuthProvider.link(user, AuthProvider.GOOGLE, userInfo.sub(), userInfo.email());
        userAuthProviderRepository.save(mapping);
        return user;
    }

    private String resolveNickname(String name, String email) {
        if (name != null && !name.isBlank()) {
            return truncate(name.trim(), 100);
        }
        int at = email.indexOf('@');
        String localPart = at > 0 ? email.substring(0, at) : email;
        return truncate(localPart, 100);
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveRefreshFamily(String refreshToken, String subject) {
        String familyId = jwtProvider.getRefreshFamily(refreshToken);
        if (familyId == null || familyId.isBlank()) {
            return "legacy:" + subject;
        }
        return familyId;
    }

    private String resolveRefreshTokenId(String refreshToken) {
        String tokenId = jwtProvider.getTokenId(refreshToken);
        if (tokenId == null || tokenId.isBlank()) {
            return "legacy:" + sha256(refreshToken);
        }
        return tokenId;
    }

    private String refreshFamilyRevokedKey(String familyId) {
        return REFRESH_REVOKED_FAMILY_KEY_PREFIX + familyId;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
