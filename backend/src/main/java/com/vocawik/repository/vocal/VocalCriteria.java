package com.vocawik.repository.vocal;

import com.vocawik.domain.resource.ResourceStatus;
import java.util.List;
import java.util.UUID;

/**
 * Search condition for vocal character listing.
 *
 * @param status optional resource status filter
 * @param query optional name keyword
 * @param songUuids optional song resource UUID filters
 * @param voicebankUuids optional voicebank resource UUID filters
 */
public record VocalCriteria(
        ResourceStatus status, String query, List<UUID> songUuids, List<UUID> voicebankUuids) {

    public VocalCriteria {
        songUuids = songUuids == null ? List.of() : List.copyOf(songUuids);
        voicebankUuids = voicebankUuids == null ? List.of() : List.copyOf(voicebankUuids);
    }
}
