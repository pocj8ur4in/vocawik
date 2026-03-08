package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed PLAYLIST payload returned by resource detail endpoint. */
public record PlaylistResourceDetailResponse(
        UUID resourceUuid,
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

    /** Creates an immutable PLAYLIST detail response. */
    public PlaylistResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        songs = List.copyOf(songs);
    }

    /** Song entry in the playlist detail payload. */
    public record PlaylistSong(
            UUID songResourceUuid,
            String songCanonicalName,
            String songThumbnailUrl,
            String songType,
            LocalDateTime publishedAt,
            int sortOrder) {}
}
