package com.vocawik.security.internal;

import com.vocawik.domain.user.User;
import com.vocawik.security.jwt.AuthPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates internal API requests with a shared token as a configured user. */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String INTERNAL_REQUEST_ATTRIBUTE = "vocawik.internalRequest";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalApiAuthenticationService internalApiAuthenticationService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        internalApiAuthenticationService
                .authenticate(token)
                .ifPresent(user -> authenticate(request, user));

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, User user) {
        String role = user.getRole().name();
        AuthPrincipal principal = new AuthPrincipal(user.getUuid(), role);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.setAttribute(INTERNAL_REQUEST_ATTRIBUTE, Boolean.TRUE);
        logger.debug("Set internal API authentication for userUuid={}", user.getUuid());
    }
}
