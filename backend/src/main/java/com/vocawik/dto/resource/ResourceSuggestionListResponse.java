package com.vocawik.dto.resource;

import java.util.List;

/** Response payload for resource suggestion queries. */
public record ResourceSuggestionListResponse(List<ResourceSuggestionElementResponse> items) {

    public ResourceSuggestionListResponse {
        items = List.copyOf(items);
    }
}
