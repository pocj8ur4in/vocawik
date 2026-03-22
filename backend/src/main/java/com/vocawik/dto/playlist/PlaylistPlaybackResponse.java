package com.vocawik.dto.playlist;

import com.vocawik.dto.song.SongPlaybackElementResponse;
import java.util.List;
import java.util.UUID;

/** Player-focused playlist payload with ordered song playback items. */
public record PlaylistPlaybackResponse(
        UUID resourceUuid,
        String canonicalName,
        String localizedName,
        String thumbnailUrl,
        List<PlaylistPlaybackSong> songs) {

    /** Creates an immutable playlist playback response. */
    public PlaylistPlaybackResponse {
        songs = List.copyOf(songs);
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
