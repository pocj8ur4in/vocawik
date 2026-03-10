package com.vocawik.dto.resource;

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
        List<PlaylistSong> songs) {

    /** Creates an immutable playlist detail response. */
    public PlaylistResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
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
