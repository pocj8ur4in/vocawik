package com.vocawik.dto.artist;

import java.util.List;

/** Response payload for artist suggestion queries. */
public record ArtistSuggestionListResponse(List<ArtistSuggestionElementResponse> items) {

    /** Creates an immutable list response. */
    public ArtistSuggestionListResponse {
        items = List.copyOf(items);
    }
}
