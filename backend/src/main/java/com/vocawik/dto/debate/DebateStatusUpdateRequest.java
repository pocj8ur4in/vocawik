package com.vocawik.dto.debate;

import jakarta.validation.constraints.NotBlank;

/** Request payload for updating a debate status. */
public record DebateStatusUpdateRequest(@NotBlank String status, String captchaToken) {}
