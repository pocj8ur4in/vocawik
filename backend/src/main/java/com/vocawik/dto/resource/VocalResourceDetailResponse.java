package com.vocawik.dto.resource;

import com.vocawik.dto.history.ResourceHistoryElementResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed vocal payload returned by resource detail endpoint. */
public record VocalResourceDetailResponse(
        UUID resourceUuid,
        boolean isDeleted,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String content,
        List<VocalLink> links,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<ResourceHistoryElementResponse> histories,
        List<VocalSong> songs) {

    /** Creates an immutable vocal detail response. */
    public VocalResourceDetailResponse {
        links = List.copyOf(links);
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        histories = List.copyOf(histories);
        songs = List.copyOf(songs);
    }

    /** Link item for the vocal detail payload. */
    public record VocalLink(String type, String url, boolean isDeleted) {}

    /** Song mapping item for the vocal detail payload. */
    public record VocalSong(
            UUID songResourceUuid,
            String songCanonicalName,
            String songThumbnailUrl,
            String songType,
            LocalDateTime publishedAt,
            boolean isMain,
            int sortOrder) {}
}
