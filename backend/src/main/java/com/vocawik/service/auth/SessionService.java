package com.vocawik.service.auth;

import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserStatus;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.jwt.JwtProvider;
import com.vocawik.web.exception.UnauthorizedException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles local session authentication and refresh-token lifecycle. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "StringRedisTemplate is a Spring-managed infrastructure bean and is not exposed externally.")
public class SessionService {

    private static final String REFRESH_USED_KEY_PREFIX = "auth:refresh:used:";
    private static final String REFRESH_REVOKED_FAMILY_KEY_PREFIX = "auth:refresh:family:revoked:";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";
    private static final int MAX_PASSWORD_FAILED_ATTEMPTS = 5;
    private static final Duration PASSWORD_LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates an user account by email and password.
     *
     * @param email user email
     * @param password raw password
     * @return issued tokens
     */
    @Transactional
    public AuthTokenBundle login(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        User user =
                userRepository
                        .findByEmailIgnoreCaseAndIsDeletedFalse(email.trim())
                        .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        user.clearPasswordLockIfExpired(now);
        if (user.isPasswordLocked(now)) {
            throw new UnauthorizedException(
                    "Account is temporarily locked due to repeated failed attempts.");
        }

        String passwordHash = user.getPasswordHash();
        boolean invalidPassword =
                passwordHash == null
                        || passwordHash.isBlank()
                        || !passwordEncoder.matches(password, passwordHash);
        if (invalidPassword) {
            user.recordPasswordFailure(MAX_PASSWORD_FAILED_ATTEMPTS, PASSWORD_LOCK_DURATION, now);
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        user.clearPasswordFailureState();
        user.touchLastLoginAt();
        return issueTokenBundle(user);
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
     * Revokes refresh token family for logout.
     *
     * @param refreshToken refresh token from cookie
     */
    public void logout(String refreshToken) {
        if (refreshToken == null || !jwtProvider.validateRefreshToken(refreshToken)) {
            return;
        }

        String subject = jwtProvider.getSubject(refreshToken);
        String familyId = resolveRefreshFamily(refreshToken, subject);
        String tokenId = resolveRefreshTokenId(refreshToken);
        Duration refreshTtl = Duration.ofSeconds(jwtProvider.getRefreshExpirationSeconds());

        stringRedisTemplate.opsForValue().set(refreshFamilyRevokedKey(familyId), "1", refreshTtl);
        stringRedisTemplate.opsForValue().set(REFRESH_USED_KEY_PREFIX + tokenId, "1", refreshTtl);
    }

    /**
     * Returns refresh token expiration in seconds.
     *
     * @return refresh token expiration (seconds)
     */
    public long getRefreshExpirationSeconds() {
        return jwtProvider.getRefreshExpirationSeconds();
    }

    /**
     * Issues a new access/refresh token bundle for an authenticated user.
     *
     * @param user authenticated user entity
     * @return issued token bundle
     */
    public AuthTokenBundle issueTokenBundle(User user) {
        String role = user.getRole().name();
        String subject = user.getUuid().toString();

        String accessToken = jwtProvider.generateAccessToken(subject, role);
        String familyId = UUID.randomUUID().toString();
        String refreshToken =
                jwtProvider.generateRefreshToken(
                        subject, role, familyId, UUID.randomUUID().toString());

        return new AuthTokenBundle(
                accessToken, refreshToken, jwtProvider.getAccessExpirationSeconds());
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
