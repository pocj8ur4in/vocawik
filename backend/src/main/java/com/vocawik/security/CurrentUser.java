package com.vocawik.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the current authenticated user's ID into a controller method parameter.
 *
 * <p>Resolved by {@link CurrentUserArgumentResolver} from the {@link
 * org.springframework.security.core.context.SecurityContextHolder}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
