package com.vocawik.service.artist;

import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.artist.ArtistElementResponse;
import com.vocawik.dto.artist.ArtistListResponse;
import com.vocawik.repository.artist.ArtistCriteria;
import com.vocawik.repository.artist.ArtistRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching artists. */
@Service
@RequiredArgsConstructor
public class ArtistService {

    private final ArtistRepository artistRepository;

    /**
     * Searches artists with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional canonical-name query
     * @param songUuids optional song resource UUID filters
     * @param groupArtistUuids optional group artist resource UUID filters
     * @param memberArtistUuids optional member artist resource UUID filters
     * @param pageable page/sort options
     * @return sliced artist list response
     */
    @Transactional(readOnly = true)
    public ArtistListResponse search(
            ResourceStatus status,
            String query,
            List<UUID> songUuids,
            List<UUID> groupArtistUuids,
            List<UUID> memberArtistUuids,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedSongUuids = normalizeUuids(songUuids);
        List<UUID> normalizedGroupArtistUuids = normalizeUuids(groupArtistUuids);
        List<UUID> normalizedMemberArtistUuids = normalizeUuids(memberArtistUuids);

        Slice<Artist> resultSlice =
                artistRepository.search(
                        new ArtistCriteria(
                                status,
                                normalizedQuery,
                                normalizedSongUuids,
                                normalizedGroupArtistUuids,
                                normalizedMemberArtistUuids),
                        pageable);

        List<ArtistElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new ArtistListResponse(
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

    private ArtistElementResponse toSummary(Artist artist) {
        Resource resource = artist.getResource();
        return new ArtistElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
