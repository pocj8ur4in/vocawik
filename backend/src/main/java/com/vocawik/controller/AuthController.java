package com.vocawik.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.dto.auth.AuthTokenResponse;
import com.vocawik.service.auth.AuthService;
import com.vocawik.service.auth.AuthTokenBundle;
import com.vocawik.service.auth.OAuthStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints for OAuth flows. */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication endpoints")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String OAUTH_STATE_COOKIE = "oauth_state";

    @Value("${security.cookie.secure:true}")
    private boolean secureCookie;

    private final AuthService authService;
    private final OAuthStateService oAuthStateService;

    /**
     * Starts OAuth authorization for the given provider.
     *
     * @param provider provider path value (e.g. google)
     * @return provider metadata and placeholder authorization URL
     */
    @GetMapping("/oauth/{provider}/authorize")
    @Operation(
            summary = "Start OAuth authorization",
            description = "Builds authorization entry data for OAuth login flow.")
    public ResponseEntity<Map<String, String>> authorize(
            @PathVariable String provider,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthProvider authProvider = parseProvider(provider);

        if (!AuthProvider.GOOGLE.equals(authProvider)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported OAuth provider.");
        }

        String state = oAuthStateService.generate();
        addOAuthStateCookie(response, state);

        return ResponseEntity.ok(
                Map.of(
                        "provider",
                        authProvider.name(),
                        "authorizeUrl",
                        authService.buildGoogleAuthorizeUrl(state)));
    }

    /**
     * Handles OAuth callback from the provider.
     *
     * @param provider provider path value (e.g. google)
     * @param code authorization code from provider
     * @param state state value for CSRF protection
     * @return callback metadata and placeholder status
     */
    @GetMapping("/oauth/{provider}/callback")
    @Operation(
            summary = "Handle OAuth callback",
            description =
                    "Receives OAuth callback query parameters and validates provider identifier.")
    public ResponseEntity<AuthTokenResponse> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String cookieState,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!AuthProvider.GOOGLE.equals(parseProvider(provider))) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported OAuth provider.");
        }
        if (!oAuthStateService.isValid(state, cookieState)) {
            clearOAuthStateCookie(response);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid OAuth state.");
        }

        AuthTokenBundle tokenBundle = authService.authenticateGoogle(code);
        clearOAuthStateCookie(response);
        addRefreshCookie(response, tokenBundle.refreshToken());
        return ResponseEntity.ok(
                new AuthTokenResponse(
                        tokenBundle.accessToken(), "Bearer", tokenBundle.expiresIn()));
    }

    /**
     * Reissues access token using refresh token context.
     *
     * @return placeholder refresh result
     */
    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Reissues a new access token from refresh token context.")
    public ResponseEntity<AuthTokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthTokenBundle tokenBundle = authService.refresh(refreshToken);
        addRefreshCookie(response, tokenBundle.refreshToken());

        return ResponseEntity.ok(
                new AuthTokenResponse(
                        tokenBundle.accessToken(), "Bearer", tokenBundle.expiresIn()));
    }

    private AuthProvider parseProvider(String provider) {
        try {
            return AuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported OAuth provider.");
        }
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie =
                ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Strict")
                        .path("/api/v1/auth")
                        .maxAge(authService.getRefreshExpirationSeconds())
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addOAuthStateCookie(HttpServletResponse response, String state) {
        ResponseCookie cookie =
                ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path("/api/v1/auth/oauth")
                        .maxAge(5 * 60)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearOAuthStateCookie(HttpServletResponse response) {
        ResponseCookie cookie =
                ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path("/api/v1/auth/oauth")
                        .maxAge(0)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
