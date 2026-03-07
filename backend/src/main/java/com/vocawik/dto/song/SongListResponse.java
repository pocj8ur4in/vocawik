package com.vocawik.dto.song;

import java.util.List;

/** Response payload for song list queries. */
public record SongListResponse(
        List<SongElementResponse> items, int page, int size, boolean hasNext) {

    /** Creates an immutable list response. */
    public SongListResponse {
        items = List.copyOf(items);
    }
}
