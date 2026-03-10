package com.vocawik.dto.resource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Detailed song payload returned by resource detail endpoint. */
public record SongResourceDetailResponse(
        UUID resourceUuid,
        String canonicalName,
        String status,
        String songType,
        long viewCount,
        String thumbnailUrl,
        String content,
        Object links,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ResourceNameDetailResponse> names,
        List<ResourceAclDetailResponse> acls,
        List<SongLyric> lyrics,
        List<SongPv> pvs,
        List<SongArtist> artists,
        List<SongVocal> vocals,
        List<SongRelation> relations,
        List<SongIncomingRelation> incomingRelations,
        List<SongPlaylist> playlists) {

    /** Creates an immutable song detail response. */
    public SongResourceDetailResponse {
        names = List.copyOf(names);
        acls = List.copyOf(acls);
        lyrics = List.copyOf(lyrics);
        pvs = List.copyOf(pvs);
        artists = List.copyOf(artists);
        vocals = List.copyOf(vocals);
        relations = List.copyOf(relations);
        incomingRelations = List.copyOf(incomingRelations);
        playlists = List.copyOf(playlists);
    }

    /** Song lyric item in the detailed payload. */
    public record SongLyric(
            UUID lyricUuid,
            List<String> langCodes,
            Object lyrics,
            boolean isPrimary,
            int sortOrder,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {

        /** Creates an immutable lyric item. */
        public SongLyric {
            langCodes = List.copyOf(langCodes);
        }
    }

    /** Song PV item in the detailed payload. */
    public record SongPv(
            UUID pvUuid,
            UUID pvViewUuid,
            String service,
            String videoKey,
            String title,
            String thumbnailUrl,
            String uploaderKey,
            Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            int sortOrder,
            long viewCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    /** Song artist item in the detailed payload. */
    public record SongArtist(
            UUID artistResourceUuid,
            String canonicalName,
            String thumbnailUrl,
            boolean isMain,
            int sortOrder,
            List<String> roles) {

        /** Creates an immutable artist item. */
        public SongArtist {
            roles = List.copyOf(roles);
        }
    }

    /** Song vocal item in the detailed payload. */
    public record SongVocal(
            UUID vocalResourceUuid, String vocalCanonicalName, boolean isMain, int sortOrder) {}

    /** Song relation item in the detailed payload. */
    public record SongRelation(
            UUID targetSongResourceUuid, String targetSongCanonicalName, String targetSongType) {}

    /** Incoming relation item in the detailed payload. */
    public record SongIncomingRelation(
            UUID sourceSongResourceUuid, String sourceSongCanonicalName, String sourceSongType) {}

    /** Playlist item that contains this song. */
    public record SongPlaylist(
            UUID playlistResourceUuid, String playlistCanonicalName, int sortOrder) {}
}
