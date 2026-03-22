package com.vocawik.dto.playlist;

import java.util.List;

/** Cursor-paginated playlist song list response. */
public record PlaylistSongListResponse(
        List<PlaylistSongElementResponse> items, String nextCursor, boolean hasNext) {

    /** Creates an immutable playlist song list response. */
    public PlaylistSongListResponse {
        items = List.copyOf(items);
    }
}
