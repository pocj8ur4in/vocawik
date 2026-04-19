package com.vocawik.module.security.context;

import com.vocawik.module.security.guest.GuestPrincipal;
import com.vocawik.module.security.jwt.AuthPrincipal;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/** Utility for reading the current Spring Security context. */
public final class SecurityContextUtils {

    /** Prevents instantiation because security context access has no instance state. */
    private SecurityContextUtils() {}

    /**
     * Returns the current authenticated {@link Authentication}.
     *
     * @return authenticated authentication or empty when missing or anonymous
     */
    public static Optional<Authentication> currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isUnauthenticated(authentication)) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }

    /**
     * Returns the current JWT principal.
     *
     * @return current user principal or empty when the principal is not a user
     */
    public static Optional<AuthPrincipal> currentUser() {
        return currentAuthentication()
                .map(Authentication::getPrincipal)
                .filter(AuthPrincipal.class::isInstance)
                .map(AuthPrincipal.class::cast);
    }

    /**
     * Returns the current guest principal.
     *
     * @return current guest principal or empty when the principal is not a guest
     */
    public static Optional<GuestPrincipal> currentGuest() {
        return currentAuthentication()
                .map(Authentication::getPrincipal)
                .filter(GuestPrincipal.class::isInstance)
                .map(GuestPrincipal.class::cast);
    }

    /**
     * Returns whether the current authentication has the given authority.
     *
     * @param authority authority name
     * @return whether the current authentication has the authority
     */
    public static boolean hasAuthority(String authority) {
        if (!StringUtils.hasText(authority)) {
            return false;
        }
        return currentAuthentication().stream()
                .flatMap(authentication -> authentication.getAuthorities().stream())
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    /**
     * Returns whether the current authentication has the given role.
     *
     * @param role role name without the {@code ROLE_} prefix
     * @return whether the current authentication has the role
     */
    public static boolean hasRole(String role) {
        if (!StringUtils.hasText(role)) {
            return false;
        }
        return hasAuthority(SecurityAuthorities.role(role));
    }

    /**
     * Returns whether the given authentication should be treated as unauthenticated.
     *
     * @param authentication authentication to inspect
     * @return whether authentication is missing or anonymous
     */
    private static boolean isUnauthenticated(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getPrincipal() == null
                || "anonymousUser".equals(authentication.getPrincipal());
    }
}
