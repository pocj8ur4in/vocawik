package com.vocawik.dto.resource;

import com.vocawik.dto.playlist.PlaylistSongElementResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed playlist payload returned by playlist detail endpoint. */
public record PlaylistResourceDetailResponse(
        UUID resourceUuid,
        boolean isDeleted,
        String canonicalName,
        String localizedName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String content,
        boolean isPublic,
        boolean isSystemManaged,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long songCount,
        String songsNextCursor,
        boolean hasMoreSongs,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<PlaylistSongElementResponse> songs) {

    /** Creates an immutable playlist detail response. */
    public PlaylistResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        songs = List.copyOf(songs);
    }
}
