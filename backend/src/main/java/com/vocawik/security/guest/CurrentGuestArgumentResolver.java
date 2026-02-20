package com.vocawik.security.guest;

import com.vocawik.web.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentGuest}-annotated controller parameters.
 *
 * <p>Extracts the guest identity from the {@link GuestPrincipal} set by {@link
 * GuestAuthenticationFilter}.
 */
@Component
public class CurrentGuestArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Supports {@link CurrentGuest}-annotated parameters that request either a guest UUID or the
     * full {@link GuestPrincipal}.
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentGuest.class)
                && (parameter.getParameterType().equals(UUID.class)
                        || parameter.getParameterType().equals(GuestPrincipal.class));
    }

    /**
     * Extracts the {@link GuestPrincipal} set by {@link GuestAuthenticationFilter} and injects the
     * value requested by the controller parameter type ({@link UUID} or {@link GuestPrincipal}).
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new UnauthorizedException("Guest authentication required.");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof GuestPrincipal guestPrincipal) {
            if (parameter.getParameterType().equals(GuestPrincipal.class)) {
                return guestPrincipal;
            }
            return guestPrincipal.guestUuid();
        }

        throw new UnauthorizedException("Invalid guest authentication principal.");
    }
}
