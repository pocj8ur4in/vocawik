package com.vocawik.dto.auth;

import java.time.Instant;

/** Response payload for registration verification request creation. */
public record RegistrationVerificationRequestResponse(String id, Instant expiresAt) {}
