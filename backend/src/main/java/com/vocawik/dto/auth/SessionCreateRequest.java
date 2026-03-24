package com.vocawik.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for session creation (email/password login). */
public record SessionCreateRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 64) String password,
        String captchaToken,
        Boolean keepSignedIn) {}
