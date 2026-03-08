package com.vocawik.service.vocal;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.vocal.VocalVoicebank;
import com.vocawik.domain.vocal.VoicebankType;
import com.vocawik.dto.voicebank.VoicebankElementResponse;
import com.vocawik.dto.voicebank.VoicebankListResponse;
import com.vocawik.repository.vocal.VocalVoicebankRepository;
import com.vocawik.repository.vocal.VoicebankCriteria;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching voicebanks. */
@Service
@RequiredArgsConstructor
public class VoicebankService {

    private final VocalVoicebankRepository vocalVoicebankRepository;

    /**
     * Searches voicebanks with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional name query
     * @param songUuids optional song resource UUID filters
     * @param vocalUuids optional vocal resource UUID filters
     * @param voicebankTypes optional voicebank type filters
     * @param pageable page/sort options
     * @return sliced voicebank list response
     */
    @Transactional(readOnly = true)
    public VoicebankListResponse search(
            ResourceStatus status,
            String query,
            List<UUID> songUuids,
            List<UUID> vocalUuids,
            List<VoicebankType> voicebankTypes,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedSongUuids = normalizeUuids(songUuids);
        List<UUID> normalizedVocalUuids = normalizeUuids(vocalUuids);
        List<VoicebankType> normalizedVoicebankTypes = normalizeVoicebankTypes(voicebankTypes);

        Slice<VocalVoicebank> resultSlice =
                vocalVoicebankRepository.search(
                        new VoicebankCriteria(
                                status,
                                normalizedQuery,
                                normalizedSongUuids,
                                normalizedVocalUuids,
                                normalizedVoicebankTypes),
                        pageable);

        List<VoicebankElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new VoicebankListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<UUID> normalizeUuids(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> normalizedSet = new LinkedHashSet<>();
        for (UUID uuid : uuids) {
            if (uuid == null) {
                throw new IllegalArgumentException("UUID filter contains null");
            }
            normalizedSet.add(uuid);
        }
        return List.copyOf(normalizedSet);
    }

    private List<VoicebankType> normalizeVoicebankTypes(List<VoicebankType> voicebankTypes) {
        if (voicebankTypes == null || voicebankTypes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<VoicebankType> normalizedSet = new LinkedHashSet<>();
        for (VoicebankType voicebankType : voicebankTypes) {
            if (voicebankType == null) {
                throw new IllegalArgumentException("voicebankTypes filter contains null");
            }
            normalizedSet.add(voicebankType);
        }
        return List.copyOf(normalizedSet);
    }

    private VoicebankElementResponse toSummary(VocalVoicebank voicebank) {
        Resource resource = voicebank.getResource();
        return new VoicebankElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                voicebank.getVoicebankType().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
