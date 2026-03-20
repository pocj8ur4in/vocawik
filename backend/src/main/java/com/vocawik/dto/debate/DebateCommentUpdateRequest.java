package com.vocawik.dto.debate;

import jakarta.validation.constraints.NotBlank;

/** Request payload for updating a debate comment. */
public record DebateCommentUpdateRequest(@NotBlank String content, String captchaToken) {}
