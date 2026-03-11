package com.vocawik.dto.history;

import java.util.List;

/** Response payload for resource history list queries. */
public record ResourceHistoryListResponse(
        List<ResourceHistoryElementResponse> items, int page, int size, long totalCount) {

    public ResourceHistoryListResponse {
        items = List.copyOf(items);
    }
}
