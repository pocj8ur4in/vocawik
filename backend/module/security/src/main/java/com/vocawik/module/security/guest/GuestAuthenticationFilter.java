package com.vocawik.module.security.guest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Authenticates allowed anonymous requests as guest principals. */
@Slf4j
public class GuestAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final GuestAuthenticationProvider guestAuthenticationProvider;

    /**
     * Creates a guest authentication filter.
     *
     * @param requestMappingHandlerMapping Spring MVC handler mapping
     * @param guestAuthenticationProvider provider for guest principals
     */
    public GuestAuthenticationFilter(
            RequestMappingHandlerMapping requestMappingHandlerMapping,
            GuestAuthenticationProvider guestAuthenticationProvider) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.guestAuthenticationProvider = guestAuthenticationProvider;
    }

    /**
     * Authenticates requests whose resolved handler allows guest access.
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
        if (SecurityContextHolder.getContext().getAuthentication() != null
                || StringUtils.hasText(request.getHeader(AUTHORIZATION_HEADER))
                || !isAllowGuestHandler(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        guestAuthenticationProvider
                .authenticate(request)
                .ifPresent(principal -> authenticate(request, principal));

        filterChain.doFilter(request, response);
    }

    /**
     * Stores guest authentication in the security context.
     *
     * @param request current HTTP request
     * @param principal guest principal
     */
    private void authenticate(HttpServletRequest request, GuestPrincipal principal) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.debug("Set guest authentication for guestUuid={}", principal.guestUuid());
    }

    /**
     * Returns whether the request handler allows guest authentication.
     *
     * @param request current HTTP request
     * @return whether guest authentication is allowed
     */
    private boolean isAllowGuestHandler(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = requestMappingHandlerMapping.getHandler(request);
            if (chain == null) {
                return false;
            }
            Object handler = chain.getHandler();
            if (handler instanceof HandlerMethod handlerMethod) {
                return handlerMethod.hasMethodAnnotation(AllowGuest.class)
                        || handlerMethod.getBeanType().isAnnotationPresent(AllowGuest.class);
            }
            return false;
        } catch (Exception ex) {
            logger.debug("Failed to resolve handler for guest authentication", ex);
            return false;
        }
    }
}
