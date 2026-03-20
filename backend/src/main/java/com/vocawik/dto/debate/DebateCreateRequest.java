package com.vocawik.dto.debate;

import jakarta.validation.constraints.NotBlank;

/** Request payload for creating a debate thread. */
public record DebateCreateRequest(
        @NotBlank String title, @NotBlank String content, String captchaToken) {}
