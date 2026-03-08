package com.vocawik.service.vocal;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.vocal.VocalCharacter;
import com.vocawik.dto.vocal.VocalElementResponse;
import com.vocawik.dto.vocal.VocalListResponse;
import com.vocawik.repository.vocal.VocalCharacterRepository;
import com.vocawik.repository.vocal.VocalCriteria;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching vocal characters. */
@Service
@RequiredArgsConstructor
public class VocalService {

    private final VocalCharacterRepository vocalCharacterRepository;

    /**
     * Searches vocal characters with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional name query
     * @param songUuids optional song resource UUID filters
     * @param voicebankUuids optional voicebank resource UUID filters
     * @param pageable page/sort options
     * @return sliced vocal list response
     */
    @Transactional(readOnly = true)
    public VocalListResponse search(
            ResourceStatus status,
            String query,
            List<UUID> songUuids,
            List<UUID> voicebankUuids,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedSongUuids = normalizeUuids(songUuids);
        List<UUID> normalizedVoicebankUuids = normalizeUuids(voicebankUuids);

        Slice<VocalCharacter> resultSlice =
                vocalCharacterRepository.search(
                        new VocalCriteria(
                                status,
                                normalizedQuery,
                                normalizedSongUuids,
                                normalizedVoicebankUuids),
                        pageable);

        List<VocalElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new VocalListResponse(
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

    private VocalElementResponse toSummary(VocalCharacter vocalCharacter) {
        Resource resource = vocalCharacter.getResource();
        return new VocalElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
