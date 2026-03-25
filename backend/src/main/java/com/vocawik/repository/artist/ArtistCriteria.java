package com.vocawik.repository.artist;

import com.vocawik.domain.resource.ResourceStatus;
import java.util.List;
import java.util.UUID;

/**
 * Search condition for artist listing.
 *
 * @param status optional resource status filter
 * @param query optional canonical-name keyword
 * @param songUuids optional song resource UUID filters
 * @param groupArtistUuids optional group artist resource UUID filters
 * @param memberArtistUuids optional member artist resource UUID filters
 * @param includeDeleted whether soft-deleted rows should be included
 */
public record ArtistCriteria(
        ResourceStatus status,
        String query,
        List<UUID> songUuids,
        List<UUID> groupArtistUuids,
        List<UUID> memberArtistUuids,
        boolean includeDeleted) {

    public ArtistCriteria {
        songUuids = songUuids == null ? List.of() : List.copyOf(songUuids);
        groupArtistUuids = groupArtistUuids == null ? List.of() : List.copyOf(groupArtistUuids);
        memberArtistUuids = memberArtistUuids == null ? List.of() : List.copyOf(memberArtistUuids);
    }
}
