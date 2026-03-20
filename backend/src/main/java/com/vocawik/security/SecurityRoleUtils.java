package com.vocawik.security;

import com.vocawik.domain.user.UserRole;
import com.vocawik.security.jwt.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utility helpers for inspecting the current authenticated role. */
public final class SecurityRoleUtils {

    private SecurityRoleUtils() {}

    /** Returns whether the current authenticated principal is an admin user. */
    public static boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        return UserRole.ADMIN.name().equals(principal.role());
    }
}
