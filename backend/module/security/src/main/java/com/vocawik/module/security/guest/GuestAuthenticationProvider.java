package com.vocawik.module.security.guest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** Provides guest principals without exposing guest storage details to the security module. */
public interface GuestAuthenticationProvider {

    /**
     * Resolves a guest principal for the current request.
     *
     * @param request current HTTP request
     * @return guest principal when authentication succeeds
     */
    Optional<GuestPrincipal> authenticate(HttpServletRequest request);
}
