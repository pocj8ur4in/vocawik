package com.vocawik.dto.resource;

import java.util.List;

/** Response payload for resource list queries. */
public record ResourceListResponse(
        List<ResourceElementResponse> items, int page, int size, boolean hasNext) {

    public ResourceListResponse {
        items = List.copyOf(items);
    }
}
