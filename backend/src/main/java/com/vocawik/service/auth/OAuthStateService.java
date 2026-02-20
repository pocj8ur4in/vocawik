package com.vocawik.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** Generates and validates OAuth state values used for CSRF protection. */
@Component
public class OAuthStateService {

    private static final int STATE_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates a new random OAuth state value.
     *
     * @return URL-safe state value
     */
    public String generate() {
        byte[] bytes = new byte[STATE_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Validates callback state against cookie state using constant-time comparison.
     *
     * @param callbackState state from query parameter
     * @param cookieState state from cookie
     * @return true if valid
     */
    public boolean isValid(String callbackState, String cookieState) {
        if (callbackState == null
                || cookieState == null
                || callbackState.isBlank()
                || cookieState.isBlank()) {
            return false;
        }
        byte[] left = callbackState.getBytes(StandardCharsets.UTF_8);
        byte[] right = cookieState.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(left, right);
    }
}
