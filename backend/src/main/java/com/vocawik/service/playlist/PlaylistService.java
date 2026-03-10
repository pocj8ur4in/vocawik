package com.vocawik.service.playlist;

import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.playlist.PlaylistElementResponse;
import com.vocawik.dto.playlist.PlaylistListResponse;
import com.vocawik.repository.playlist.PlaylistCriteria;
import com.vocawik.repository.playlist.PlaylistRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for playlist list queries. */
@Service
@RequiredArgsConstructor
public class PlaylistService {

    private final PlaylistRepository playlistRepository;

    @Transactional(readOnly = true)
    public PlaylistListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Slice<Playlist> resultSlice =
                playlistRepository.search(new PlaylistCriteria(status, normalizedQuery), pageable);

        List<PlaylistElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new PlaylistListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PlaylistElementResponse toSummary(Playlist playlist) {
        Resource resource = playlist.getResource();
        return new PlaylistElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
