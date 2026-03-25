package com.vocawik.dto.playlist;

import com.vocawik.dto.song.SongPlaybackElementResponse;
import java.util.List;
import java.util.UUID;

/** Cursor-paginated player-focused playlist payload with ordered song playback items. */
public record PlaylistPlaybackListResponse(
        UUID resourceUuid,
        String canonicalName,
        String localizedName,
        String thumbnailUrl,
        List<PlaylistPlaybackSong> items,
        String nextCursor,
        boolean hasNext) {

    /** Creates an immutable playlist playback response. */
    public PlaylistPlaybackListResponse {
        items = List.copyOf(items);
    }

    /** Ordered song item in a playlist playback response. */
    public record PlaylistPlaybackSong(
            UUID resourceUuid,
            String canonicalName,
            String localizedName,
            String thumbnailUrl,
            String subtitle,
            int sortOrder,
            List<SongPlaybackElementResponse.SongPlaybackPv> pvs) {

        /** Creates an immutable playlist playback song item. */
        public PlaylistPlaybackSong {
            pvs = List.copyOf(pvs);
        }
    }
}
