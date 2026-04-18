package com.vocawik.module.security.user;

import com.vocawik.module.security.jwt.AuthPrincipal;
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

/** Resolves {@link CurrentUser}-annotated controller parameters. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /** Creates a current user argument resolver. */
    public CurrentUserArgumentResolver() {}

    /**
     * Returns whether this resolver supports the given controller method parameter.
     *
     * @param parameter controller method parameter
     * @return whether the parameter is annotated with {@link CurrentUser} and has UUID type
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    /**
     * Resolves the current authenticated user's UUID from the security context.
     *
     * @param parameter controller method parameter
     * @param mavContainer model and view container
     * @param webRequest current web request
     * @param binderFactory data binder factory
     * @return current authenticated user's UUID
     */
    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isUnauthenticated(authentication)) {
            throw unauthorized("Authentication required.");
        }

        return resolveUserUuid(authentication);
    }

    /**
     * Extracts the user UUID from the authentication principal.
     *
     * @param authentication current authentication
     * @return authenticated user's UUID
     */
    private UUID resolveUserUuid(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            return authPrincipal.userUuid();
        }

        throw unauthorized("Invalid authentication principal.");
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
