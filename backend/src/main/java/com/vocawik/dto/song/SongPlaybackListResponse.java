package com.vocawik.dto.song;

import java.util.List;

/** Response payload for player-focused song searches. */
public record SongPlaybackListResponse(List<SongPlaybackElementResponse> items, long totalCount) {

    /** Creates an immutable playback list response. */
    public SongPlaybackListResponse {
        items = List.copyOf(items);
    }
}
