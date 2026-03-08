package com.vocawik.service.song;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongType;
import com.vocawik.dto.song.SongElementResponse;
import com.vocawik.dto.song.SongListResponse;
import com.vocawik.repository.song.SongCriteria;
import com.vocawik.repository.song.SongRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching songs. */
@Service
@RequiredArgsConstructor
public class SongService {

    private final SongRepository songRepository;

    /**
     * Searches songs with optional filters.
     *
     * @param status optional resource status filter
     * @param songType optional song type filter
     * @param query optional canonical-name query
     * @param artistUuids optional artist resource UUIDs
     * @param vocalUuids optional vocal resource UUIDs
     * @param publishedFrom optional published-at start datetime (inclusive)
     * @param publishedTo optional published-at end datetime (inclusive)
     * @param pageable page/sort options
     * @return sliced song list response
     */
    @Transactional(readOnly = true)
    public SongListResponse search(
            ResourceStatus status,
            SongType songType,
            String query,
            List<UUID> artistUuids,
            List<UUID> vocalUuids,
            LocalDateTime publishedFrom,
            LocalDateTime publishedTo,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedArtistUuids = normalizeUuids(artistUuids);
        List<UUID> normalizedVocalUuids = normalizeUuids(vocalUuids);

        Slice<Song> resultSlice =
                songRepository.search(
                        new SongCriteria(
                                status,
                                songType,
                                normalizedQuery,
                                normalizedArtistUuids,
                                normalizedVocalUuids,
                                publishedFrom,
                                publishedTo),
                        pageable);

        List<SongElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new SongListResponse(
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

    private SongElementResponse toSummary(Song song) {
        Resource resource = song.getResource();
        return new SongElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                song.getSongType().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                song.getPublishedAt(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
