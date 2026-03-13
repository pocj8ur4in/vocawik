package com.vocawik.dto.resource;

import java.util.UUID;

/** Summary item for recent popular resources. */
public record PopularResourceElementResponse(
        UUID resourceUuid, String resourceType, String canonicalName, long recentViewCount) {}
