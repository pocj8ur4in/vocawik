package com.vocawik.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for registration verification request creation. */
public record RegistrationVerificationRequestCreateRequest(
        @NotBlank @Email @Size(max = 254) String email) {}
