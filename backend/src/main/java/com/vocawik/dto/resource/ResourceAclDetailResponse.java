package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resource ACL item for detail payloads. */
public record ResourceAclDetailResponse(
        UUID aclUuid,
        String action,
        String subjectType,
        String subjectValue,
        String effect,
        int priority,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
