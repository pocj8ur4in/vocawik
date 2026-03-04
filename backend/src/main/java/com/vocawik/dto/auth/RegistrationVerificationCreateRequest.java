package com.vocawik.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for POST /registration-verifications. */
public record RegistrationVerificationCreateRequest(
        @NotBlank String token, @Size(max = 36) String requestId) {}
