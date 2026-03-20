package com.vocawik.dto.debate;

import java.util.List;

/** Response payload for resource debate listings. */
public record DebateListResponse(List<DebateListElementResponse> items) {

    public DebateListResponse {
        items = List.copyOf(items);
    }
}
