package com.vocawik.module.security.guest;

import com.vocawik.module.web.error.BusinessException;
import com.vocawik.module.web.error.ErrorCode;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentGuest}-annotated controller parameters. */
@Component
public class CurrentGuestArgumentResolver implements HandlerMethodArgumentResolver {

    /** Creates a current guest argument resolver. */
    public CurrentGuestArgumentResolver() {}

    /**
     * Returns whether this resolver supports the given controller method parameter.
     *
     * @param parameter controller method parameter
     * @return whether the parameter is annotated with {@link CurrentGuest} and has UUID type
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentGuest.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    /**
     * Resolves the current authenticated guest's UUID from the security context.
     *
     * @param parameter controller method parameter
     * @param mavContainer model and view container
     * @param webRequest current web request
     * @param binderFactory data binder factory
     * @return current authenticated guest's UUID
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isUnauthenticated(authentication)) {
            throw unauthorized("Guest authentication required.");
        }

        return resolveGuestUuid(authentication);
    }

    /**
     * Extracts the guest UUID from the authentication principal.
     *
     * @param authentication current authentication
     * @return authenticated guest's UUID
     */
    private UUID resolveGuestUuid(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof GuestPrincipal guestPrincipal) {
            return guestPrincipal.guestUuid();
        }

        throw unauthorized("Invalid guest authentication principal.");
    }

    /**
     * Returns whether the current authentication should be treated as unauthenticated.
     *
     * @param authentication current authentication
     * @return whether authentication is missing or anonymous
     */
    private boolean isUnauthenticated(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal());
    }

    /**
     * Creates an unauthorized business exception.
     *
     * @param message client-safe error message
     * @return unauthorized business exception
     */
    private BusinessException unauthorized(String message) {
        return new BusinessException(ErrorCode.UNAUTHORIZED, message);
    }
}
