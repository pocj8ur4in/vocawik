package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed voicebank payload returned by resource detail endpoint. */
public record VoicebankResourceDetailResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String voicebankType,
        String content,
        Object links,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<VoicebankSong> songs) {

    /** Creates an immutable voicebank detail response. */
    public VoicebankResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        songs = List.copyOf(songs);
    }

    /** Song mapping item for the voicebank detail payload. */
    public record VoicebankSong(
            UUID songResourceUuid,
            String songCanonicalName,
            String songThumbnailUrl,
            String songType,
            LocalDateTime publishedAt,
            UUID vocalResourceUuid,
            String vocalCanonicalName,
            boolean isMain,
            int sortOrder) {}
}
