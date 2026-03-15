package com.vocawik.dto.song;

import java.util.List;

/** Response payload for song suggestion queries. */
public record SongSuggestionListResponse(List<SongSuggestionElementResponse> items) {

    /** Creates an immutable list response. */
    public SongSuggestionListResponse {
        items = List.copyOf(items);
    }
}
