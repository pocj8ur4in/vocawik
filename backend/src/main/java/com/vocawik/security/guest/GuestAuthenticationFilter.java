package com.vocawik.security.guest;

import com.vocawik.domain.guest.Guest;
import com.vocawik.web.ClientIpResolver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Authenticates a request as a guest when access token is not present and the handler method is
 * annotated with {@link AllowGuest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "RequestMappingHandlerMapping is managed by Spring and not exposed externally.")
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;

    private final ClientIpResolver clientIpResolver;
    private final GuestIdentityService guestIdentityService;

    /**
     * Applies guest authentication only when:
     *
     * <ul>
     *   <li>No authentication is already present in the security context
     *   <li>No {@code Authorization} header is present (token-based auth should handle it)
     *   <li>The resolved handler method is annotated with {@link AllowGuest}
     * </ul>
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!isAllowGuestHandler(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIpResolver.resolve(request);
        Guest guest = guestIdentityService.findOrCreateByIp(ip);

        GuestPrincipal principal = new GuestPrincipal(java.util.UUID.fromString(guest.getUuid()));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        logger.debug("Set guest authentication for guestUuid={}", principal.guestUuid());

        filterChain.doFilter(request, response);
    }

    private boolean isAllowGuestHandler(HttpServletRequest request) {
        try {
            HandlerExecutionChain chain = requestMappingHandlerMapping.getHandler(request);
            if (chain == null) {
                return false;
            }
            Object handler = chain.getHandler();
            if (handler instanceof HandlerMethod handlerMethod) {
                return handlerMethod.hasMethodAnnotation(AllowGuest.class);
            }
            return false;
        } catch (Exception e) {
            logger.debug("Failed to resolve handler for guest auth", e);
            return false;
        }
    }
}
