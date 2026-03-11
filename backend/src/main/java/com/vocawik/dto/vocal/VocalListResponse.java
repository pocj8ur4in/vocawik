package com.vocawik.dto.vocal;

import java.util.List;

/** Response payload for vocal list queries. */
public record VocalListResponse(
        List<VocalElementResponse> items, int page, int size, long totalCount) {

    /** Creates an immutable list response. */
    public VocalListResponse {
        items = List.copyOf(items);
    }
}
