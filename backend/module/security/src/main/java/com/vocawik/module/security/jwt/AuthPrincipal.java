package com.vocawik.module.security.jwt;

import java.util.UUID;

/**
 * Authenticated principal extracted from JWT claims.
 *
 * @param userUuid authenticated user UUID
 * @param role authenticated user role
 */
public record AuthPrincipal(UUID userUuid, String role) {}
