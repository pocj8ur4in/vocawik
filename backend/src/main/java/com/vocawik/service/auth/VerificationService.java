package com.vocawik.service.auth;

import com.vocawik.infrastructure.mail.EmailService;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Manages registration email verification requests.
 *
 * <ul>
 *   <li>Creates and stores one-time verification tokens in Redis
 *   <li>Sends verification emails through {@link EmailService}
 *   <li>Prevents duplicate pending requests for the same email
 * </ul>
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "StringRedisTemplate is a Spring-managed infrastructure bean and is not exposed externally.")
public class VerificationService {

    private static final int EMAIL_VERIFY_TOKEN_BYTES = 32;
    private static final String EMAIL_VERIFY_KEY_PREFIX = "auth:email:verify:";
    private static final String EMAIL_VERIFY_SIGNUP_EMAIL_KEY_PREFIX =
            "auth:email:verify:signup:email:";
    private static final String EMAIL_VERIFY_SIGNUP_VALUE_PREFIX = "signup:";
    private static final Duration EMAIL_VERIFY_TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration EMAIL_VERIFY_REQUEST_LOCK_TTL = Duration.ofMinutes(11);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final EmailService emailService;

    @Value("${auth.email-verification.frontend-url}")
    private String emailVerificationFrontendUrl;

    @Value("${auth.email-verification.subject}")
    private String emailVerificationSubject;

    @Value("${auth.email-verification.content-template}")
    private String emailVerificationContentTemplate;

    @PostConstruct
    void validateEmailVerificationProperties() {
        if (emailVerificationFrontendUrl == null || emailVerificationFrontendUrl.isBlank()) {
            throw new IllegalStateException(
                    "auth.email-verification.frontend-url must be configured");
        }
        if (emailVerificationSubject == null || emailVerificationSubject.isBlank()) {
            throw new IllegalStateException("auth.email-verification.subject must be configured");
        }
        if (emailVerificationContentTemplate == null
                || emailVerificationContentTemplate.isBlank()) {
            throw new IllegalStateException(
                    "auth.email-verification.content-template must be configured");
        }
    }

    /**
     * Creates a verification request for a registration email.
     *
     * @param email email to verify
     * @return generated verification request identifier
     */
    public String requestEmailVerification(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        boolean emailExists =
                userRepository.findByEmailIgnoreCaseAndIsDeletedFalse(normalizedEmail).isPresent();
        if (emailExists) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        String requestId = UUID.randomUUID().toString();
        String signupEmailVerifyKey = signupEmailVerifyKey(normalizedEmail);
        Boolean reserved =
                stringRedisTemplate
                        .opsForValue()
                        .setIfAbsent(
                                signupEmailVerifyKey, requestId, EMAIL_VERIFY_REQUEST_LOCK_TTL);
        if (!Boolean.TRUE.equals(reserved)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ALREADY_REQUESTED);
        }

        try {
            sendSignupEmailVerification(normalizedEmail, requestId);
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(signupEmailVerifyKey);
            throw ex;
        }

        return requestId;
    }

    /**
     * Returns the verification token lifetime in seconds.
     *
     * @return verification token TTL in seconds
     */
    public long getEmailVerificationTtlSeconds() {
        return EMAIL_VERIFY_TOKEN_TTL.toSeconds();
    }

    private void sendSignupEmailVerification(String email, String requestId) {
        String rawToken = generateEmailVerificationToken();
        String tokenHash = sha256(rawToken);
        String tokenKey = emailVerifyKey(tokenHash);
        stringRedisTemplate
                .opsForValue()
                .set(
                        tokenKey,
                        EMAIL_VERIFY_SIGNUP_VALUE_PREFIX + requestId + ":" + email,
                        EMAIL_VERIFY_TOKEN_TTL);

        String verifyUrl = buildFrontendVerifyUrl(rawToken);
        try {
            emailService.send(
                    email,
                    resolveEmailVerificationSubject(),
                    buildEmailVerificationContent(verifyUrl));
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(tokenKey);
            throw ex;
        }
    }

    private String buildFrontendVerifyUrl(String rawToken) {
        String baseUrl = emailVerificationFrontendUrl.trim();
        String delimiter = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + delimiter + "token=" + encode(rawToken);
    }

    private String resolveEmailVerificationSubject() {
        if (emailVerificationSubject == null || emailVerificationSubject.isBlank()) {
            throw new IllegalStateException("auth.email-verification.subject must be configured");
        }
        return emailVerificationSubject.trim();
    }

    private String buildEmailVerificationContent(String verifyUrl) {
        if (emailVerificationContentTemplate == null
                || emailVerificationContentTemplate.isBlank()) {
            throw new IllegalStateException(
                    "auth.email-verification.content-template must be configured");
        }
        return emailVerificationContentTemplate
                .replace("{verifyUrl}", verifyUrl)
                .replace("\\n", "\n");
    }

    private String generateEmailVerificationToken() {
        byte[] bytes = new byte[EMAIL_VERIFY_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String emailVerifyKey(String tokenHash) {
        return EMAIL_VERIFY_KEY_PREFIX + tokenHash;
    }

    private String signupEmailVerifyKey(String email) {
        return EMAIL_VERIFY_SIGNUP_EMAIL_KEY_PREFIX + sha256(email.trim().toLowerCase(Locale.ROOT));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
