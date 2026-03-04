package com.vocawik.dto.auth;

import java.util.UUID;

/** Response payload for account registration creation. */
public record RegistrationResponse(UUID userId) {}
