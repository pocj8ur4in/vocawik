package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed artist payload returned by resource detail endpoint. */
public record ArtistResourceDetailResponse(
        UUID resourceUuid,
        boolean isDeleted,
        String canonicalName,
        String status,
        long viewCount,
        String thumbnailUrl,
        String content,
        List<ArtistLink> links,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<ArtistSong> songs,
        List<ArtistGroup> groups,
        List<ArtistMember> members) {

    /** Creates an immutable artist detail response. */
    public ArtistResourceDetailResponse {
        links = List.copyOf(links);
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        songs = List.copyOf(songs);
        groups = List.copyOf(groups);
        members = List.copyOf(members);
    }

    /** Link item for the artist detail payload. */
    public record ArtistLink(String type, String url, String content, boolean isDeleted) {}

    /** Song participation item for the artist detail payload. */
    public record ArtistSong(
            UUID songResourceUuid,
            String songCanonicalName,
            String songThumbnailUrl,
            String songType,
            LocalDateTime publishedAt,
            boolean isMain,
            int sortOrder,
            List<String> roles) {

        /** Creates an immutable artist song item. */
        public ArtistSong {
            roles = List.copyOf(roles);
        }
    }

    /** Group-membership item where current artist is the group owner. */
    public record ArtistGroup(
            UUID memberArtistResourceUuid,
            String memberArtistCanonicalName,
            String memberArtistThumbnailUrl,
            int sortOrder) {}

    /** Group-membership item where current artist is a member of another group. */
    public record ArtistMember(
            UUID groupArtistResourceUuid,
            String groupArtistCanonicalName,
            String groupArtistThumbnailUrl,
            int sortOrder) {}
}
