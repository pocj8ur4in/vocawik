package com.vocawik.repository.vocal;

import com.vocawik.domain.resource.ResourceStatus;
import java.util.List;
import java.util.UUID;

/**
 * Search condition for vocal listing.
 *
 * @param status optional resource status filter
 * @param query optional name keyword
 * @param songUuids optional song resource UUID filters
 * @param includeDeleted whether soft-deleted rows should be included
 */
public record VocalCriteria(
        ResourceStatus status, String query, List<UUID> songUuids, boolean includeDeleted) {

    public VocalCriteria {
        songUuids = songUuids == null ? List.of() : List.copyOf(songUuids);
    }
}
