package com.vocawik.repository.song;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.SongType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Search condition for song listing.
 *
 * @param status optional resource status filter
 * @param songType optional song type filter
 * @param query optional canonical-name keyword
 * @param artistUuids optional artist resource UUID filters
 * @param vocalUuids optional vocal resource UUID filters
 * @param publishedFrom optional published-at start datetime (inclusive)
 * @param publishedTo optional published-at end datetime (inclusive)
 */
public record SongSearchCondition(
        ResourceStatus status,
        SongType songType,
        String query,
        List<UUID> artistUuids,
        List<UUID> vocalUuids,
        LocalDateTime publishedFrom,
        LocalDateTime publishedTo) {

    public SongSearchCondition {
        artistUuids = artistUuids == null ? List.of() : List.copyOf(artistUuids);
        vocalUuids = vocalUuids == null ? List.of() : List.copyOf(vocalUuids);
    }
}
