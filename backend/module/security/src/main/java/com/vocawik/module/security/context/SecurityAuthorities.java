package com.vocawik.module.security.context;

import org.springframework.util.StringUtils;

/** Utility for creating Spring Security authority names. */
public final class SecurityAuthorities {

    public static final String ROLE_PREFIX = "ROLE_";

    /** Prevents instantiation because authority normalization has no instance state. */
    private SecurityAuthorities() {}

    /**
     * Creates a role authority name.
     *
     * @param role role name with or without the {@code ROLE_} prefix
     * @return role authority name
     * @throws IllegalArgumentException if the role is missing or blank
     */
    public static String role(String role) {
        if (!StringUtils.hasText(role)) {
            throw new IllegalArgumentException("role is required.");
        }
        String value = role.trim();
        return value.startsWith(ROLE_PREFIX) ? value : ROLE_PREFIX + value;
    }
}
