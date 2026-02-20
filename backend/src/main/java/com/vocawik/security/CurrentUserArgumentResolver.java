package com.vocawik.security;

import com.vocawik.security.jwt.AuthPrincipal;
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
 * Resolves {@link CurrentUser}-annotated controller parameters.
 *
 * <p>Extracts the user ID from the {@link AuthPrincipal} stored by {@link
 * com.vocawik.security.jwt.JwtFilter}.
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    /**
     * Returns {@code true} if the parameter is annotated with {@link CurrentUser} and is either
     * {@link UUID} or {@link AuthPrincipal}.
     *
     * @param parameter the method parameter
     * @return whether this resolver supports the parameter
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && (parameter.getParameterType().equals(UUID.class)
                        || parameter.getParameterType().equals(AuthPrincipal.class));
    }

    /**
     * Extracts the user ID from the current authentication context.
     *
     * @param parameter the method parameter
     * @param mavContainer the model and view container
     * @param webRequest the current request
     * @param binderFactory the binder factory
     * @return the authenticated user's UUID
     * @throws UnauthorizedException if no authentication is present
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
            throw new UnauthorizedException("Authentication required.");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            if (parameter.getParameterType().equals(AuthPrincipal.class)) {
                return authPrincipal;
            }
            return authPrincipal.userUuid();
        }

        throw new UnauthorizedException("Invalid authentication principal.");
    }
}
