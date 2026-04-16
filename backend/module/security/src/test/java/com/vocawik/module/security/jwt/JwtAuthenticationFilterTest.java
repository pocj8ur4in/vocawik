package com.vocawik.module.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String SECRET =
            "dGVzdC1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLW9ubHktbXVzdC1iZS1hdC1sZWFzdC0yNTYtYml0cy1sb25n";

    private final JwtProvider jwtProvider =
            new JwtProvider(
                    new JwtProperties(
                            SECRET,
                            "vocawik",
                            "vocawik-api",
                            Duration.ofHours(1),
                            Duration.ofDays(30)));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate valid bearer access token")
    void doFilter_withValidAccessToken_shouldSetAuthentication() throws Exception {
        UUID userUuid = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                "Authorization",
                "Bearer " + jwtProvider.generateAccessToken(userUuid.toString(), "USER"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new AuthPrincipal(userUuid, "USER"));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Should ignore invalid bearer token")
    void doFilter_withInvalidAccessToken_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.value");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should ignore non-bearer authorization header")
    void doFilter_withNonBearerHeader_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic token");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should ignore access token with invalid subject")
    void doFilter_withInvalidSubject_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                "Authorization",
                "Bearer "
                        + JwtTestTokens.signedToken(
                                SECRET,
                                builder ->
                                        builder.subject("not-a-uuid")
                                                .claim("role", "USER")
                                                .claim("typ", "ACCESS")));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should ignore access token without subject")
    void doFilter_withoutSubject_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                "Authorization",
                "Bearer "
                        + JwtTestTokens.signedToken(
                                SECRET,
                                builder -> builder.claim("role", "USER").claim("typ", "ACCESS")));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should ignore access token with malformed role")
    void doFilter_withMalformedRole_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                "Authorization",
                "Bearer "
                        + JwtTestTokens.signedToken(
                                SECRET,
                                builder ->
                                        builder.subject(UUID.randomUUID().toString())
                                                .claim("role", 123)
                                                .claim("typ", "ACCESS")));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Should ignore access token without role")
    void doFilter_withoutRole_shouldNotSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(
                "Authorization",
                "Bearer "
                        + JwtTestTokens.signedToken(
                                SECRET,
                                builder ->
                                        builder.subject(UUID.randomUUID().toString())
                                                .claim("typ", "ACCESS")));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
