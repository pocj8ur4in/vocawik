package com.vocawik.dto.song;

/** Response payload for resolved song PV metadata. */
public record SongPvResolveResponse(
        String service,
        String videoKey,
        String title,
        String thumbnailUrl,
        String uploaderKey,
        Integer durationSeconds,
        String publishedAt,
        boolean isDuplicated,
        SongPvResolveExtra extra) {

    /** Provider-specific metadata payload. */
    public record SongPvResolveExtra(String audioUrl, Long cid, String externalUrl) {}
}
