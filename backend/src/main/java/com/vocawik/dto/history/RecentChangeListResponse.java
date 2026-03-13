package com.vocawik.dto.history;

import java.util.List;

/** Response payload for the global recent changes feed. */
public record RecentChangeListResponse(List<RecentChangeElementResponse> items, int size) {

    public RecentChangeListResponse {
        items = List.copyOf(items);
    }
}
