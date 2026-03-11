package com.vocawik.dto.resource;

import java.util.List;

/** Response payload for resource list queries. */
public record ResourceListResponse(
        List<ResourceElementResponse> items, int page, int size, long totalCount) {

    public ResourceListResponse {
        items = List.copyOf(items);
    }
}
