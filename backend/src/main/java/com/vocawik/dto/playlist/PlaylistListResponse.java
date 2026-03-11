package com.vocawik.dto.playlist;

import java.util.List;

/** Response payload for playlist list queries. */
public record PlaylistListResponse(
        List<PlaylistElementResponse> items, int page, int size, long totalCount) {

    /** Creates an immutable list response. */
    public PlaylistListResponse {
        items = List.copyOf(items);
    }
}
