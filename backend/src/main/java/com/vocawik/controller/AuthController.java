package com.vocawik.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.dto.auth.RegistrationCreateRequest;
import com.vocawik.dto.auth.RegistrationVerificationCreateRequest;
import com.vocawik.dto.auth.RegistrationVerificationRequestCreateRequest;
import com.vocawik.dto.auth.RegistrationVerificationRequestResponse;
import com.vocawik.dto.auth.RegistrationVerificationResponse;
import com.vocawik.dto.auth.SessionCreateRequest;
import com.vocawik.dto.auth.SessionTokenResponse;
import com.vocawik.service.auth.AuthTokenBundle;
import com.vocawik.service.auth.SessionService;
import com.vocawik.service.auth.VerificationService;
import com.vocawik.service.auth.oauth.OAuthService;
import com.vocawik.service.auth.oauth.OAuthStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints for Auth flows. */
@RestController
@Tag(name = "Auth", description = "Authentication endpoints")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final String OAUTH_STATE_COOKIE = "oauth_state";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/sessions";
    private static final String OAUTH_STATE_COOKIE_PATH = "/api/v1/oauth";

    @Value("${security.cookie.secure:true}")
    private boolean secureCookie;

    private final SessionService sessionService;
    private final OAuthService oAuthService;
    private final VerificationService verificationService;
    private final OAuthStateService oAuthStateService;

    /**
     * Requests email verification for register.
     *
     * @param request request body with email
     * @return verification request result payload
     */
    @PostMapping("/registration-verification-requests")
    @Operation(
            summary = "Verify request for register",
            description = "Creates an email verification request for registration.")
    public ResponseEntity<RegistrationVerificationRequestResponse> requestEmailVerification(
            @Valid @RequestBody RegistrationVerificationRequestCreateRequest request) {
        String requestId = verificationService.requestEmailVerification(request.email());
        Instant expiresAt =
                Instant.now().plusSeconds(verificationService.getEmailVerificationTtlSeconds());

        return ResponseEntity.created(
                        URI.create("/api/v1/registration-verification-requests/" + requestId))
                .body(new RegistrationVerificationRequestResponse(requestId, expiresAt));
    }

    /**
     * Confirms one-time email verification token.
     *
     * @param request verification request body
     * @return verification result payload with register ticket
     */
    @PostMapping("/registration-verifications")
    @Operation(
            summary = "Create registration verification result",
            description = "Confirms verification token and issues a register ticket.")
    public ResponseEntity<RegistrationVerificationResponse> confirmEmailVerification(
            @Valid @RequestBody RegistrationVerificationCreateRequest request) {
        String registerTicket =
                verificationService.confirmEmailVerification(request.token(), request.requestId());
        if (registerTicket.isBlank()) {
            return ResponseEntity.status(CREATED)
                    .body(new RegistrationVerificationResponse(null, null));
        }
        Instant expiresAt =
                Instant.now().plusSeconds(verificationService.getRegisterTicketTtlSeconds());
        return ResponseEntity.status(CREATED)
                .body(new RegistrationVerificationResponse(registerTicket, expiresAt));
    }

    @PostMapping("/registrations")
    @Operation(
            summary = "Create registration",
            description = "Creates a user account from verified register ticket.")
    public ResponseEntity<Void> register(@Valid @RequestBody RegistrationCreateRequest request) {
        UUID userUuid =
                verificationService.register(
                        request.password(), request.nickname(), request.registerTicket());
        return ResponseEntity.status(CREATED)
                .location(URI.create("/api/v1/users/" + userUuid))
                .build();
    }

    /**
     * Authenticates email/password credentials and issues token bundle.
     *
     * @param request login request body
     * @param response servlet response for refresh token cookie
     * @return issued access token payload
     */
    @PostMapping("/sessions")
    @Operation(
            summary = "Create session",
            description = "Authenticates credentials and creates an access/refresh session.")
    public ResponseEntity<SessionTokenResponse> login(
            @Valid @RequestBody SessionCreateRequest request, HttpServletResponse response) {
        AuthTokenBundle tokenBundle = sessionService.login(request.email(), request.password());
        addRefreshCookie(response, tokenBundle.refreshToken());
        return ResponseEntity.created(URI.create("/api/v1/sessions/current"))
                .body(
                        new SessionTokenResponse(
                                tokenBundle.accessToken(), "Bearer", tokenBundle.expiresIn()));
    }

    /**
     * Reissues access token using refresh token context.
     *
     * @return placeholder refresh result
     */
    @PostMapping("/sessions/refresh")
    @Operation(
            summary = "Refresh access token",
            description = "Reissues a new access token from refresh token context.")
    public ResponseEntity<SessionTokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        AuthTokenBundle tokenBundle = sessionService.refresh(refreshToken);
        addRefreshCookie(response, tokenBundle.refreshToken());

        return ResponseEntity.ok(
                new SessionTokenResponse(
                        tokenBundle.accessToken(), "Bearer", tokenBundle.expiresIn()));
    }

    /**
     * Logs out current session and clears refresh token cookie.
     *
     * @param refreshToken refresh token cookie value
     * @param response servlet response for cookie cleanup
     * @return empty object response
     */
    @DeleteMapping("/sessions/current")
    @Operation(
            summary = "Delete current session",
            description = "Revokes refresh token family and clears refresh token cookie.")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        sessionService.logout(refreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.status(NO_CONTENT).build();
    }

    /**
     * Starts OAuth authorization for the given provider.
     *
     * @param provider provider path value (e.g. google)
     * @return provider metadata and placeholder authorization URL
     */
    @GetMapping("/oauth/authorizations/{provider}")
    @Operation(
            summary = "Start OAuth authorization",
            description = "Builds authorization entry data for OAuth login flow.")
    public ResponseEntity<Map<String, String>> authorize(
            @PathVariable String provider, HttpServletResponse response) {
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
                        oAuthService.buildGoogleAuthorizeUrl(state)));
    }

    /**
     * Handles OAuth callback from the provider.
     *
     * @param provider provider path value (e.g. google)
     * @param code authorization code from provider
     * @param state state value for CSRF protection
     * @return callback metadata and placeholder status
     */
    @GetMapping("/oauth/callbacks/{provider}")
    @Operation(
            summary = "Handle OAuth callback",
            description =
                    "Receives OAuth callback query parameters and validates provider identifier.")
    public ResponseEntity<SessionTokenResponse> callback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @CookieValue(name = OAUTH_STATE_COOKIE, required = false) String cookieState,
            HttpServletResponse response) {
        if (!AuthProvider.GOOGLE.equals(parseProvider(provider))) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported OAuth provider.");
        }
        if (!oAuthStateService.isValid(state, cookieState)) {
            clearOAuthStateCookie(response);
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid OAuth state.");
        }

        AuthTokenBundle tokenBundle = oAuthService.authenticateGoogle(code);
        clearOAuthStateCookie(response);
        addRefreshCookie(response, tokenBundle.refreshToken());
        return ResponseEntity.ok(
                new SessionTokenResponse(
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
                        .path(REFRESH_COOKIE_PATH)
                        .maxAge(sessionService.getRefreshExpirationSeconds())
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie =
                ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Strict")
                        .path(REFRESH_COOKIE_PATH)
                        .maxAge(0)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void addOAuthStateCookie(HttpServletResponse response, String state) {
        ResponseCookie cookie =
                ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                        .httpOnly(true)
                        .secure(secureCookie)
                        .sameSite("Lax")
                        .path(OAUTH_STATE_COOKIE_PATH)
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
                        .path(OAUTH_STATE_COOKIE_PATH)
                        .maxAge(0)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
