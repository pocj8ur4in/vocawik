package com.vocawik.repository.vocal;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.vocal.VoicebankType;
import java.util.List;
import java.util.UUID;

/**
 * Search condition for voicebank listing.
 *
 * @param status optional resource status filter
 * @param query optional name keyword
 * @param songUuids optional song resource UUID filters
 * @param vocalUuids optional vocal resource UUID filters
 * @param voicebankTypes optional voicebank type filters
 */
public record VoicebankCriteria(
        ResourceStatus status,
        String query,
        List<UUID> songUuids,
        List<UUID> vocalUuids,
        List<VoicebankType> voicebankTypes) {

    public VoicebankCriteria {
        songUuids = songUuids == null ? List.of() : List.copyOf(songUuids);
        vocalUuids = vocalUuids == null ? List.of() : List.copyOf(vocalUuids);
        voicebankTypes = voicebankTypes == null ? List.of() : List.copyOf(voicebankTypes);
    }
}
