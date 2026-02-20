package com.vocawik.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter.
 *
 * <p>Extracts the JWT from header and sets the authentication in {@link SecurityContextHolder} if
 * valid.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    /**
     * Filters the request and sets the authentication in the {@link SecurityContextHolder}.
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain filter chain
     * @throws ServletException if a servlet exception occurs
     * @throws IOException if an I/O exception occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtProvider.validateAccessToken(token)) {
            String subject = jwtProvider.getSubject(token);
            String role = jwtProvider.getRole(token);

            // check role exists
            List<GrantedAuthority> authorities =
                    role == null || role.isBlank()
                            ? List.of()
                            : List.of(new SimpleGrantedAuthority("ROLE_" + role));

            // parse subject as UUID to build AuthPrincipal
            AuthPrincipal principal;
            try {
                principal = new AuthPrincipal(java.util.UUID.fromString(subject), role);
            } catch (IllegalArgumentException ex) {
                logger.warn("Invalid JWT subject (not a UUID)");
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.debug("Set authentication for: {}", principal.userUuid());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearer) && bearer.startsWith(BEARER_PREFIX)) {
            return bearer.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
