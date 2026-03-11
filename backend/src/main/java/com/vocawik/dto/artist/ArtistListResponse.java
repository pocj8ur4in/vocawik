package com.vocawik.dto.artist;

import java.util.List;

/** Response payload for artist list queries. */
public record ArtistListResponse(
        List<ArtistElementResponse> items, int page, int size, long totalCount) {

    /** Creates an immutable list response. */
    public ArtistListResponse {
        items = List.copyOf(items);
    }
}
