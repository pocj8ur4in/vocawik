package com.vocawik.service.pv.client;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;

/** API client contract for fetching PV metadata by provider. */
public interface PvMetaApiClient {

    /** Provider supported by client. */
    SongPvProvider provider();

    /** Fetches provider metadata for a detected PV URL. */
    PvMetaResult fetch(DetectedPv detectedPv);

    /** Provider-agnostic metadata payload. */
    record PvMetaResult(
            String videoKey,
            String title,
            String thumbnailUrl,
            String uploaderKey,
            Integer durationSeconds,
            String publishedAt,
            PvMetaExtra extra) {

        public PvMetaResult(
                String videoKey,
                String title,
                String thumbnailUrl,
                String uploaderKey,
                Integer durationSeconds,
                String publishedAt) {
            this(videoKey, title, thumbnailUrl, uploaderKey, durationSeconds, publishedAt, null);
        }
    }

    /** Optional provider-specific metadata payload. */
    record PvMetaExtra(String audioUrl, Long cid, String externalUrl) {}
}
