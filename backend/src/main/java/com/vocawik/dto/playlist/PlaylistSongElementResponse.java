package com.vocawik.dto.playlist;

import java.util.UUID;

/** Song item returned for playlist song listings. */
public record PlaylistSongElementResponse(
        UUID songResourceUuid,
        String songCanonicalName,
        String songLocalizedName,
        String songThumbnailUrl,
        String songType,
        int sortOrder) {}
