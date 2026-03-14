package com.vocawik.infrastructure.pv.model;

import com.vocawik.domain.song.SongPvProvider;

/** Detected provider metadata extracted from URL. */
public record DetectedPv(SongPvProvider provider, String videoKey, String normalizedUrl) {

    public DetectedPv {
        if (provider == null) {
            throw new IllegalArgumentException("provider is required");
        }
        if (videoKey == null || videoKey.isBlank()) {
            throw new IllegalArgumentException("videoKey is required");
        }
        if (normalizedUrl == null || normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("normalizedUrl is required");
        }
    }
}
