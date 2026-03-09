package com.vocawik.service.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.resource.ArtistResourceDetailResponse;
import com.vocawik.dto.resource.PlaylistResourceDetailResponse;
import com.vocawik.dto.resource.ResourceAclDetailResponse;
import com.vocawik.dto.resource.ResourceElementResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.dto.resource.ResourceNameDetailResponse;
import com.vocawik.dto.resource.SongResourceDetailResponse;
import com.vocawik.dto.resource.VocalResourceDetailResponse;
import com.vocawik.dto.resource.VoicebankResourceDetailResponse;
import com.vocawik.repository.resource.ResourceCriteria;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching resources. */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    /**
     * Searches active resources with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional canonical-name query
     * @param pageable page/sort options
     * @return paged resource list response
     */
    @Transactional(readOnly = true)
    public ResourceListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Slice<Resource> resultSlice =
                resourceRepository.search(new ResourceCriteria(status, normalizedQuery), pageable);

        List<ResourceElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new ResourceListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
    }

    /**
     * Finds resource detail from {@code resources.data}.
     *
     * @param resourceUuid resource UUID
     * @return denormalized resource detail payload
     */
    @Transactional(readOnly = true)
    public Object getByResourceUuid(UUID resourceUuid) {
        Resource resource =
                resourceRepository
                        .findByUuidAndIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (resource.getData() == null || resource.getData().isNull()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return switch (resource.getResourceType()) {
            case SONG -> toSongResourceDetail(resource);
            case ARTIST -> toArtistResourceDetail(resource);
            case VOCAL -> toVocalResourceDetail(resource);
            case VOICEBANK -> toVoicebankResourceDetail(resource);
            case PLAYLIST -> toPlaylistResourceDetail(resource);
            default -> toJsonValue(resource.getData());
        };
    }

    /**
     * Finds song detail from {@code resources.data}.
     *
     * @param resourceUuid song resource UUID
     * @return denormalized song detail payload
     */
    @Transactional(readOnly = true)
    public SongResourceDetailResponse getSongByResourceUuid(UUID resourceUuid) {
        Object detail = getByResourceUuid(resourceUuid);
        if (detail instanceof SongResourceDetailResponse songDetail) {
            return songDetail;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * Finds artist detail from {@code resources.data}.
     *
     * @param resourceUuid artist resource UUID
     * @return denormalized artist detail payload
     */
    @Transactional(readOnly = true)
    public ArtistResourceDetailResponse getArtistByResourceUuid(UUID resourceUuid) {
        Object detail = getByResourceUuid(resourceUuid);
        if (detail instanceof ArtistResourceDetailResponse artistDetail) {
            return artistDetail;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * Finds voicebank detail from {@code resources.data}.
     *
     * @param resourceUuid voicebank resource UUID
     * @return denormalized voicebank detail payload
     */
    @Transactional(readOnly = true)
    public VoicebankResourceDetailResponse getVoicebankByResourceUuid(UUID resourceUuid) {
        Object detail = getByResourceUuid(resourceUuid);
        if (detail instanceof VoicebankResourceDetailResponse voicebankDetail) {
            return voicebankDetail;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /**
     * Finds vocal detail from {@code resources.data}.
     *
     * @param resourceUuid vocal resource UUID
     * @return denormalized vocal detail payload
     */
    @Transactional(readOnly = true)
    public VocalResourceDetailResponse getVocalByResourceUuid(UUID resourceUuid) {
        Object detail = getByResourceUuid(resourceUuid);
        if (detail instanceof VocalResourceDetailResponse vocalDetail) {
            return vocalDetail;
        }
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResourceElementResponse toSummary(Resource resource) {
        return new ResourceElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getResourceType().name(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    private SongResourceDetailResponse toSongResourceDetail(Resource resource) {
        JsonNode data = resource.getData();
        return new SongResourceDetailResponse(
                asUuid(data.get("resourceUuid"), resource.getUuid()),
                asText(data.get("canonicalName"), resource.getCanonicalName()),
                asText(data.get("status"), resource.getStatus().name()),
                asText(data.get("songType"), null),
                asLong(data.get("viewCount"), resource.getViewCount()),
                asText(data.get("thumbnailUrl"), resource.getThumbnailUrl()),
                asText(data.get("content"), null),
                asJson(data.get("links"), List.of()),
                asDateTime(data.get("publishedAt"), null),
                asDateTime(data.get("createdAt"), resource.getCreatedAt()),
                asDateTime(data.get("updatedAt"), resource.getUpdatedAt()),
                mapArray(data.get("names"), this::toResourceName),
                mapArray(data.get("acls"), this::toResourceAcl),
                mapArray(data.get("lyrics"), this::toSongLyric),
                mapArray(data.get("pvs"), this::toSongPv),
                mapArray(data.get("artists"), this::toSongArtist),
                mapArray(data.get("vocals"), this::toSongVocal),
                mapArray(extractSongRelations(data.get("relations")), this::toSongRelation),
                mapArray(data.get("incomingRelations"), this::toSongIncomingRelation),
                mapArray(data.get("playlists"), this::toSongPlaylist));
    }

    private ArtistResourceDetailResponse toArtistResourceDetail(Resource resource) {
        JsonNode data = resource.getData();
        return new ArtistResourceDetailResponse(
                asUuid(data.get("resourceUuid"), resource.getUuid()),
                asText(data.get("canonicalName"), resource.getCanonicalName()),
                asText(data.get("status"), resource.getStatus().name()),
                asLong(data.get("viewCount"), resource.getViewCount()),
                asText(data.get("thumbnailUrl"), resource.getThumbnailUrl()),
                asText(data.get("content"), null),
                asJson(data.get("links"), List.of()),
                asDateTime(data.get("createdAt"), resource.getCreatedAt()),
                asDateTime(data.get("updatedAt"), resource.getUpdatedAt()),
                mapArray(data.get("names"), this::toResourceName),
                mapArray(data.get("acls"), this::toResourceAcl),
                mapArray(data.get("songs"), this::toArtistSong),
                mapArray(data.get("groups"), this::toArtistGroup),
                mapArray(extractArtistMembers(data), this::toArtistMember));
    }

    private VocalResourceDetailResponse toVocalResourceDetail(Resource resource) {
        JsonNode data = resource.getData();
        return new VocalResourceDetailResponse(
                asUuid(data.get("resourceUuid"), resource.getUuid()),
                asText(data.get("canonicalName"), resource.getCanonicalName()),
                asText(data.get("status"), resource.getStatus().name()),
                asLong(data.get("viewCount"), resource.getViewCount()),
                asText(data.get("thumbnailUrl"), resource.getThumbnailUrl()),
                asText(data.get("content"), null),
                asJson(data.get("links"), List.of()),
                asDateTime(data.get("createdAt"), resource.getCreatedAt()),
                asDateTime(data.get("updatedAt"), resource.getUpdatedAt()),
                mapArray(data.get("names"), this::toResourceName),
                mapArray(data.get("acls"), this::toResourceAcl),
                mapArray(data.get("songs"), this::toVocalSong));
    }

    private VoicebankResourceDetailResponse toVoicebankResourceDetail(Resource resource) {
        JsonNode data = resource.getData();
        return new VoicebankResourceDetailResponse(
                asUuid(data.get("resourceUuid"), resource.getUuid()),
                asText(data.get("canonicalName"), resource.getCanonicalName()),
                asText(data.get("status"), resource.getStatus().name()),
                asLong(data.get("viewCount"), resource.getViewCount()),
                asText(data.get("thumbnailUrl"), resource.getThumbnailUrl()),
                asUuid(data.get("vocalResourceUuid"), null),
                asText(data.get("vocalCanonicalName"), null),
                asText(data.get("voicebankType"), null),
                asText(data.get("content"), null),
                asJson(data.get("links"), List.of()),
                asDateTime(data.get("createdAt"), resource.getCreatedAt()),
                asDateTime(data.get("updatedAt"), resource.getUpdatedAt()),
                mapArray(data.get("names"), this::toResourceName),
                mapArray(data.get("acls"), this::toResourceAcl),
                mapArray(data.get("songs"), this::toVoicebankSong));
    }

    private PlaylistResourceDetailResponse toPlaylistResourceDetail(Resource resource) {
        JsonNode data = resource.getData();
        return new PlaylistResourceDetailResponse(
                asUuid(data.get("resourceUuid"), resource.getUuid()),
                asText(data.get("canonicalName"), resource.getCanonicalName()),
                asText(data.get("status"), resource.getStatus().name()),
                asLong(data.get("viewCount"), resource.getViewCount()),
                asText(data.get("thumbnailUrl"), resource.getThumbnailUrl()),
                asText(data.get("content"), null),
                asBoolean(data.get("isPublic"), false),
                asDateTime(data.get("createdAt"), resource.getCreatedAt()),
                asDateTime(data.get("updatedAt"), resource.getUpdatedAt()),
                mapArray(data.get("names"), this::toResourceName),
                mapArray(data.get("acls"), this::toResourceAcl),
                mapArray(data.get("songs"), this::toPlaylistSong));
    }

    private SongResourceDetailResponse.SongLyric toSongLyric(JsonNode node) {
        return new SongResourceDetailResponse.SongLyric(
                asUuid(node.get("lyricUuid"), null),
                asStringList(node.get("langCodes")),
                asJson(node.get("lyrics"), List.of()),
                asBoolean(node.get("isPrimary"), false),
                asInt(node.get("sortOrder"), 0),
                asDateTime(node.get("createdAt"), null),
                asDateTime(node.get("updatedAt"), null));
    }

    private SongResourceDetailResponse.SongPv toSongPv(JsonNode node) {
        return new SongResourceDetailResponse.SongPv(
                asUuid(node.get("pvUuid"), null),
                asUuid(node.get("pvViewUuid"), null),
                asText(node.get("service"), null),
                asText(node.get("videoKey"), null),
                asText(node.get("title"), null),
                asText(node.get("thumbnailUrl"), null),
                asText(node.get("uploaderKey"), null),
                asIntNullable(node.get("durationSeconds")),
                asBoolean(node.get("isOfficial"), false),
                asDateTime(node.get("publishedAt"), null),
                asInt(node.get("sortOrder"), 0),
                asLong(node.get("viewCount"), 0L),
                asDateTime(node.get("createdAt"), null),
                asDateTime(node.get("updatedAt"), null));
    }

    private SongResourceDetailResponse.SongArtist toSongArtist(JsonNode node) {
        return new SongResourceDetailResponse.SongArtist(
                asUuid(node.get("artistResourceUuid"), null),
                asText(node.get("canonicalName"), null),
                asText(node.get("thumbnailUrl"), null),
                asBoolean(node.get("isMain"), false),
                asInt(node.get("sortOrder"), 0),
                asStringList(node.get("roles")));
    }

    private SongResourceDetailResponse.SongVocal toSongVocal(JsonNode node) {
        return new SongResourceDetailResponse.SongVocal(
                asUuid(node.get("vocalResourceUuid"), null),
                asText(node.get("vocalCanonicalName"), null),
                asUuid(node.get("voicebankResourceUuid"), null),
                asText(node.get("voicebankCanonicalName"), null),
                asText(node.get("voicebankType"), null),
                asBoolean(node.get("isMain"), false),
                asInt(node.get("sortOrder"), 0));
    }

    private SongResourceDetailResponse.SongRelation toSongRelation(JsonNode node) {
        return new SongResourceDetailResponse.SongRelation(
                asUuid(node.get("targetSongResourceUuid"), null),
                asText(node.get("targetSongCanonicalName"), null),
                asText(node.get("targetSongType"), null));
    }

    private SongResourceDetailResponse.SongIncomingRelation toSongIncomingRelation(JsonNode node) {
        return new SongResourceDetailResponse.SongIncomingRelation(
                asUuid(node.get("sourceSongResourceUuid"), null),
                asText(node.get("sourceSongCanonicalName"), null),
                asText(node.get("sourceSongType"), null));
    }

    private SongResourceDetailResponse.SongPlaylist toSongPlaylist(JsonNode node) {
        return new SongResourceDetailResponse.SongPlaylist(
                asUuid(node.get("playlistResourceUuid"), null),
                asText(node.get("playlistCanonicalName"), null),
                asInt(node.get("sortOrder"), 0));
    }

    private ArtistResourceDetailResponse.ArtistSong toArtistSong(JsonNode node) {
        return new ArtistResourceDetailResponse.ArtistSong(
                asUuid(node.get("songResourceUuid"), null),
                asText(node.get("songCanonicalName"), null),
                asText(node.get("songThumbnailUrl"), null),
                asText(node.get("songType"), null),
                asDateTime(node.get("publishedAt"), null),
                asBoolean(node.get("isMain"), false),
                asInt(node.get("sortOrder"), 0),
                asStringList(node.get("roles")));
    }

    private ArtistResourceDetailResponse.ArtistGroup toArtistGroup(JsonNode node) {
        return new ArtistResourceDetailResponse.ArtistGroup(
                asUuid(node.get("memberArtistResourceUuid"), null),
                asText(node.get("memberArtistCanonicalName"), null),
                asText(node.get("memberArtistThumbnailUrl"), null),
                asInt(node.get("sortOrder"), 0));
    }

    private ArtistResourceDetailResponse.ArtistMember toArtistMember(JsonNode node) {
        return new ArtistResourceDetailResponse.ArtistMember(
                asUuid(node.get("groupArtistResourceUuid"), null),
                asText(node.get("groupArtistCanonicalName"), null),
                asText(node.get("groupArtistThumbnailUrl"), null),
                asInt(node.get("sortOrder"), 0));
    }

    private VocalResourceDetailResponse.VocalSong toVocalSong(JsonNode node) {
        return new VocalResourceDetailResponse.VocalSong(
                asUuid(node.get("songResourceUuid"), null),
                asText(node.get("songCanonicalName"), null),
                asText(node.get("songThumbnailUrl"), null),
                asText(node.get("songType"), null),
                asDateTime(node.get("publishedAt"), null),
                asUuid(node.get("voicebankResourceUuid"), null),
                asText(node.get("voicebankCanonicalName"), null),
                asText(node.get("voicebankType"), null),
                asBoolean(node.get("isMain"), false),
                asInt(node.get("sortOrder"), 0));
    }

    private VoicebankResourceDetailResponse.VoicebankSong toVoicebankSong(JsonNode node) {
        return new VoicebankResourceDetailResponse.VoicebankSong(
                asUuid(node.get("songResourceUuid"), null),
                asText(node.get("songCanonicalName"), null),
                asText(node.get("songThumbnailUrl"), null),
                asText(node.get("songType"), null),
                asDateTime(node.get("publishedAt"), null),
                asUuid(node.get("vocalResourceUuid"), null),
                asText(node.get("vocalCanonicalName"), null),
                asBoolean(node.get("isMain"), false),
                asInt(node.get("sortOrder"), 0));
    }

    private PlaylistResourceDetailResponse.PlaylistSong toPlaylistSong(JsonNode node) {
        return new PlaylistResourceDetailResponse.PlaylistSong(
                asUuid(node.get("songResourceUuid"), null),
                asText(node.get("songCanonicalName"), null),
                asText(node.get("songThumbnailUrl"), null),
                asText(node.get("songType"), null),
                asDateTime(node.get("publishedAt"), null),
                asInt(node.get("sortOrder"), 0));
    }

    private ResourceNameDetailResponse toResourceName(JsonNode node) {
        return new ResourceNameDetailResponse(
                asUuid(node.get("nameUuid"), null),
                asText(node.get("langCode"), null),
                asText(node.get("name"), null),
                asBoolean(node.get("isPrimary"), false),
                asInt(node.get("sortOrder"), 0),
                asDateTime(node.get("createdAt"), null),
                asDateTime(node.get("updatedAt"), null));
    }

    private ResourceAclDetailResponse toResourceAcl(JsonNode node) {
        return new ResourceAclDetailResponse(
                asUuid(node.get("aclUuid"), null),
                asText(node.get("action"), null),
                asText(node.get("subjectType"), null),
                asText(node.get("subjectValue"), null),
                asText(node.get("effect"), null),
                asInt(node.get("priority"), 0),
                asDateTime(node.get("expiresAt"), null),
                asDateTime(node.get("createdAt"), null),
                asDateTime(node.get("updatedAt"), null));
    }

    private JsonNode extractSongRelations(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        if (node.isObject() && node.has("outgoing") && node.get("outgoing").isArray()) {
            return node.get("outgoing");
        }
        return null;
    }

    private JsonNode extractArtistMembers(JsonNode data) {
        JsonNode members = data.get("members");
        if (members != null && members.isArray()) {
            return members;
        }
        JsonNode memberships = data.get("memberships");
        if (memberships != null && memberships.isArray()) {
            return memberships;
        }
        return null;
    }

    private <T> List<T> mapArray(JsonNode node, Function<JsonNode, T> mapper) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isObject)
                .map(mapper)
                .toList();
    }

    private String asText(JsonNode node, String fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        return node.asText();
    }

    private UUID asUuid(JsonNode node, UUID fallback) {
        String value = asText(node, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private long asLong(JsonNode node, long fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        try {
            return Long.parseLong(node.asText());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int asInt(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        try {
            return Integer.parseInt(node.asText());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Integer asIntNullable(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        try {
            return Integer.valueOf(node.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean asBoolean(JsonNode node, boolean fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return Boolean.parseBoolean(node.asText());
    }

    private LocalDateTime asDateTime(JsonNode node, LocalDateTime fallback) {
        String value = asText(node, null);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private Object asJson(JsonNode node, Object fallback) {
        Object value = toJsonValue(node);
        return value == null ? fallback : value;
    }

    private List<String> asStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(node.spliterator(), false)
                .map(item -> asText(item, null))
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private Object toJsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            ArrayList<Object> values = new ArrayList<>();
            for (JsonNode child : node) {
                values.add(toJsonValue(child));
            }
            return values;
        }
        if (node.isObject()) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            node.properties()
                    .forEach(entry -> values.put(entry.getKey(), toJsonValue(entry.getValue())));
            return values;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }
}
