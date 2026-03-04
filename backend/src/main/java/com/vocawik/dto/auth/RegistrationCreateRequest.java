package com.vocawik.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for account registration creation. */
public record RegistrationCreateRequest(
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(min = 2, max = 10) String nickname,
        @NotBlank String registerTicket) {}
