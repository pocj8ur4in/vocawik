package com.vocawik.dto.auth;

import java.time.Instant;

/** Response payload for registration verification confirmation. */
public record RegistrationVerificationResponse(String signupTicket, Instant expiresAt) {}
