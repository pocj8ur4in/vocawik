package com.vocawik.dto.playlist;

import java.util.List;

/** Response payload for playlist suggestion queries. */
public record PlaylistSuggestionListResponse(List<PlaylistSuggestionElementResponse> items) {

    /** Creates an immutable suggestion list response. */
    public PlaylistSuggestionListResponse {
        items = List.copyOf(items);
    }
}
