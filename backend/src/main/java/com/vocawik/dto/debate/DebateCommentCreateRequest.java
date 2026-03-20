package com.vocawik.dto.debate;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Request payload for creating a debate comment. */
public record DebateCommentCreateRequest(
        @NotBlank String content, UUID parentCommentUuid, String captchaToken) {}
