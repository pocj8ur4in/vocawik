package com.vocawik.security.jwt;

import java.util.UUID;

/** Authenticated principal extracted from a JWT. */
public record AuthPrincipal(UUID userUuid, String role) {}
