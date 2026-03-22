package com.vocawik.dto.song;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Player-focused song item returned by playback search endpoint. */
public record SongPlaybackElementResponse(
        UUID resourceUuid,
        String canonicalName,
        String localizedName,
        String thumbnailUrl,
        String subtitle,
        List<SongPlaybackPv> pvs) {

    /** Creates an immutable playback item. */
    public SongPlaybackElementResponse {
        pvs = List.copyOf(pvs);
    }

    /** PV item in the playback payload. */
    public record SongPlaybackPv(
            UUID pvUuid,
            String service,
            String videoKey,
            String url,
            String audioUrl,
            String title,
            String thumbnailUrl,
            String uploaderKey,
            Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            SongPlaybackPvExtra extra,
            int sortOrder) {}

    /** Provider-specific extra metadata for a playback PV. */
    public record SongPlaybackPvExtra(String audioUrl, Long cid, String externalUrl) {}
}
