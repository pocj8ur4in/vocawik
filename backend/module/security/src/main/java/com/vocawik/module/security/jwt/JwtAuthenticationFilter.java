package com.vocawik.module.security.jwt;

import com.vocawik.module.security.context.SecurityAuthorities;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates requests with a valid JWT access token. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    /**
     * Creates a JWT authentication filter.
     *
     * @param jwtProvider JWT token provider
     */
    public JwtAuthenticationFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    /**
     * Resolves and validates a bearer access token before the request reaches controllers.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain servlet filter chain
     * @throws ServletException if downstream servlet processing fails
     * @throws IOException if downstream I/O processing fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            jwtProvider
                    .parseAccessToken(token)
                    .ifPresent(principal -> authenticate(request, principal));
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Stores a validated JWT principal in the security context.
     *
     * @param request current HTTP request
     * @param principal validated JWT principal
     */
    private void authenticate(HttpServletRequest request, AuthPrincipal principal) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        SecurityAuthorities.role(principal.role()))));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Extracts the bearer token from the Authorization header.
     *
     * @param request current HTTP request
     * @return bearer token or {@code null} when absent
     */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearer) && bearer.startsWith(BEARER_PREFIX)) {
            String token = bearer.substring(BEARER_PREFIX.length());
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }
}
