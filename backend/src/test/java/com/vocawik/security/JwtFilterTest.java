package com.vocawik.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtFilterTest {

    // Mocks for dependencies
    private JwtProvider jwtProvider;
    private JwtFilter jwtFilter;
    private FilterChain filterChain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        // Initialize mocks
        jwtProvider = mock(JwtProvider.class);
        jwtFilter = new JwtFilter(jwtProvider);
        filterChain = mock(FilterChain.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Clear security context each
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        // Clean up security context
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Valid token sets authentication")
    void doFilterInternal_withValidToken_shouldSetAuthentication()
            throws ServletException, IOException {
        String subject = UUID.randomUUID().toString();
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        given(jwtProvider.validateAccessToken(token)).willReturn(true);
        given(jwtProvider.getSubject(token)).willReturn(subject);

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(subject);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Invalid token should not set authentication")
    void doFilterInternal_withInvalidToken_shouldNotSetAuthentication()
            throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid.token");
        given(jwtProvider.validateAccessToken("invalid.token")).willReturn(false);

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("No Authorization header should not set authentication")
    void doFilterInternal_withoutHeader_shouldNotSetAuthentication()
            throws ServletException, IOException {
        jwtFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Non-Bearer header should not set authentication")
    void doFilterInternal_withNonBearerHeader_shouldNotSetAuthentication()
            throws ServletException, IOException {
        request.addHeader("Authorization", "Basic abc123");

        jwtFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
