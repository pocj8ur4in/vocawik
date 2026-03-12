package com.vocawik.dto.resource;

import com.vocawik.dto.history.ResourceHistoryElementResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed playlist payload returned by playlist detail endpoint. */
public record PlaylistResourceDetailResponse(
        UUID resourceUuid,
        boolean isDeleted,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String content,
        boolean isPublic,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<ResourceHistoryElementResponse> histories,
        List<PlaylistSong> songs) {

    /** Creates an immutable playlist detail response. */
    public PlaylistResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        histories = List.copyOf(histories);
        songs = List.copyOf(songs);
    }

    /** Song item in the detailed playlist payload. */
    public record PlaylistSong(
            UUID songResourceUuid,
            String songCanonicalName,
            String songThumbnailUrl,
            String songType,
            int sortOrder) {}
}
