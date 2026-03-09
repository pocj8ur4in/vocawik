package com.vocawik.service.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vocawik.domain.acl.Acl;
import com.vocawik.domain.acl.AclAction;
import com.vocawik.domain.acl.AclEffect;
import com.vocawik.domain.acl.AclSubjectType;
import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongArtist;
import com.vocawik.domain.song.SongArtistRole;
import com.vocawik.domain.song.SongLyric;
import com.vocawik.domain.song.SongPv;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.domain.song.SongRelation;
import com.vocawik.domain.song.SongType;
import com.vocawik.domain.song.SongVocal;
import com.vocawik.domain.vocal.VocalCharacter;
import com.vocawik.domain.vocal.VocalVoicebank;
import com.vocawik.dto.song.SongCreateRequest;
import com.vocawik.dto.song.SongElementResponse;
import com.vocawik.dto.song.SongListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongCriteria;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalCharacterRepository;
import com.vocawik.repository.vocal.VocalVoicebankRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching songs. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ObjectMapper is a Spring-managed infrastructure bean and is not exposed externally.")
public class SongService {

    private final SongRepository songRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final SongLyricRepository songLyricRepository;
    private final SongPvRepository songPvRepository;
    private final SongArtistRepository songArtistRepository;
    private final SongVocalRepository songVocalRepository;
    private final SongRelationRepository songRelationRepository;
    private final ArtistRepository artistRepository;
    private final VocalCharacterRepository vocalCharacterRepository;
    private final VocalVoicebankRepository vocalVoicebankRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * Searches songs with optional filters.
     *
     * @param status optional resource status filter
     * @param songType optional song type filter
     * @param query optional canonical-name query
     * @param artistUuids optional artist resource UUIDs
     * @param vocalUuids optional vocal resource UUIDs
     * @param publishedFrom optional published-at start datetime (inclusive)
     * @param publishedTo optional published-at end datetime (inclusive)
     * @param pageable page/sort options
     * @return sliced song list response
     */
    @Transactional(readOnly = true)
    public SongListResponse search(
            ResourceStatus status,
            SongType songType,
            String query,
            List<UUID> artistUuids,
            List<UUID> vocalUuids,
            LocalDateTime publishedFrom,
            LocalDateTime publishedTo,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedArtistUuids = normalizeUuids(artistUuids);
        List<UUID> normalizedVocalUuids = normalizeUuids(vocalUuids);

        Slice<Song> resultSlice =
                songRepository.search(
                        new SongCriteria(
                                status,
                                songType,
                                normalizedQuery,
                                normalizedArtistUuids,
                                normalizedVocalUuids,
                                publishedFrom,
                                publishedTo),
                        pageable);

        List<SongElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new SongListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
    }

    /**
     * Creates a song and initializes resource projection payload.
     *
     * @param request create payload
     * @return created song resource UUID
     */
    @Transactional
    public UUID create(SongCreateRequest request) {
        JsonNode links = toJsonNode(request.links());
        validateLinks(links);

        Song song =
                Song.create(
                        normalizeCanonicalName(request.canonicalName()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()),
                        links,
                        request.publishedAt(),
                        parseSongType(request.songType()));

        Resource resource = resourceRepository.save(song.getResource());
        songRepository.save(song);

        List<ResourceName> resourceNames = saveResourceNames(resource, request.names());
        List<Acl> acls = saveAcls(resource, request.acls());
        List<SongLyric> lyrics = saveSongLyrics(song, request.lyrics());
        List<SongPv> pvs = saveSongPvs(song, request.pvs());
        List<SongArtist> artists = saveSongArtists(song, request.artists());
        List<SongVocal> vocals = saveSongVocals(song, request.vocals());
        List<SongRelation> relations = saveSongRelations(song, request.relations());

        resource.updateData(
                buildSongProjection(
                        song,
                        resource,
                        resourceNames,
                        acls,
                        lyrics,
                        pvs,
                        artists,
                        vocals,
                        relations));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<UUID> normalizeUuids(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> normalizedSet = new LinkedHashSet<>();
        for (UUID uuid : uuids) {
            if (uuid == null) {
                throw new IllegalArgumentException("UUID filter contains null");
            }
            normalizedSet.add(uuid);
        }
        return List.copyOf(normalizedSet);
    }

    private String normalizeCanonicalName(String canonicalName) {
        if (canonicalName == null) {
            throw new IllegalArgumentException("canonicalName is required");
        }
        String trimmed = canonicalName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("canonicalName is required");
        }
        return trimmed;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private void validateLinks(JsonNode links) {
        if (links != null && !links.isArray()) {
            throw new IllegalArgumentException("links must be a JSON array");
        }
    }

    private List<ResourceName> saveResourceNames(
            Resource resource, List<SongCreateRequest.ResourceNameCreateRequest> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }

        HashSet<String> uniqueNames = new HashSet<>();
        HashSet<String> primaryLangs = new HashSet<>();
        List<ResourceName> entities =
                names.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "names contains null item");
                                    }
                                    String normalizedName = normalizeCanonicalName(item.name());
                                    String uniqueKey =
                                            item.langCode().name() + "|" + normalizedName;
                                    if (!uniqueNames.add(uniqueKey)) {
                                        throw new IllegalArgumentException(
                                                "Duplicate resource name for language and value");
                                    }
                                    if (item.isPrimary()
                                            && !primaryLangs.add(item.langCode().name())) {
                                        throw new IllegalArgumentException(
                                                "Only one primary name is allowed per language");
                                    }

                                    return ResourceName.create(
                                            resource,
                                            item.langCode(),
                                            normalizedName,
                                            item.isPrimary(),
                                            item.sortOrder() == null ? 0 : item.sortOrder());
                                })
                        .toList();

        return resourceNameRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(ResourceName::getSortOrder)
                                .thenComparing(ResourceName::getId))
                .toList();
    }

    private List<Acl> saveAcls(
            Resource resource, List<SongCreateRequest.ResourceAclCreateRequest> acls) {
        if (acls == null || acls.isEmpty()) {
            return List.of();
        }

        List<Acl> entities =
                acls.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "acls contains null item");
                                    }
                                    AclSubjectType subjectType =
                                            parseAclSubjectType(item.subjectType());
                                    String normalizedSubjectValue =
                                            normalizeAclSubjectValue(
                                                    subjectType, item.subjectValue());
                                    return Acl.create(
                                            resource,
                                            parseAclAction(item.action()),
                                            subjectType,
                                            normalizedSubjectValue,
                                            parseAclEffect(item.effect()),
                                            item.priority() == null ? 100 : item.priority(),
                                            item.expiresAt());
                                })
                        .toList();

        return aclRepository.saveAllAndFlush(entities).stream()
                .sorted(Comparator.comparingInt(Acl::getPriority).thenComparing(Acl::getId))
                .toList();
    }

    private List<SongLyric> saveSongLyrics(
            Song song, List<SongCreateRequest.SongLyricCreateRequest> lyrics) {
        if (lyrics == null || lyrics.isEmpty()) {
            return List.of();
        }

        List<SongLyric> entities =
                lyrics.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "lyrics contains null item");
                                    }
                                    return SongLyric.create(
                                            song,
                                            item.langCodes(),
                                            toRequiredJsonNode(item.lyrics(), "lyrics.lyrics"),
                                            item.isPrimary(),
                                            item.sortOrder() == null ? 0 : item.sortOrder());
                                })
                        .toList();

        return songLyricRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(SongLyric::getSortOrder)
                                .thenComparing(SongLyric::getId))
                .toList();
    }

    private List<SongPv> saveSongPvs(Song song, List<SongCreateRequest.SongPvCreateRequest> pvs) {
        if (pvs == null || pvs.isEmpty()) {
            return List.of();
        }

        List<SongPv> entities =
                pvs.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "pvs contains null item");
                                    }
                                    return SongPv.create(
                                            song,
                                            parseSongPvProvider(item.service()),
                                            normalizeRequired(item.videoKey(), "videoKey"),
                                            normalizeNullable(item.title()),
                                            normalizeNullable(item.thumbnailUrl()),
                                            normalizeNullable(item.uploaderKey()),
                                            item.durationSeconds(),
                                            item.isOfficial(),
                                            item.publishedAt(),
                                            item.sortOrder() == null ? 0 : item.sortOrder());
                                })
                        .toList();

        return songPvRepository.saveAllAndFlush(entities);
    }

    private List<SongArtist> saveSongArtists(
            Song song, List<SongCreateRequest.SongArtistCreateRequest> artists) {
        if (artists == null || artists.isEmpty()) {
            return List.of();
        }
        validateNoNullItems("artists", artists);

        List<UUID> artistUuids =
                artists.stream()
                        .map(SongCreateRequest.SongArtistCreateRequest::artistResourceUuid)
                        .distinct()
                        .toList();
        Map<UUID, Long> artistIdsByUuid = fetchArtistIdsByResourceUuid(artistUuids);

        List<SongArtist> entities =
                artists.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "artists contains null item");
                                    }
                                    Long artistId = artistIdsByUuid.get(item.artistResourceUuid());
                                    if (artistId == null) {
                                        throw new IllegalArgumentException(
                                                "Unknown artistResourceUuid: "
                                                        + item.artistResourceUuid());
                                    }
                                    Artist artist =
                                            entityManager.getReference(Artist.class, artistId);
                                    return SongArtist.create(
                                            song,
                                            artist,
                                            parseSongArtistRoles(item.roles()),
                                            item.isMain(),
                                            item.sortOrder() == null ? 0 : item.sortOrder());
                                })
                        .toList();

        return songArtistRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(SongArtist::getSortOrder)
                                .thenComparing(SongArtist::getId))
                .toList();
    }

    private List<SongVocal> saveSongVocals(
            Song song, List<SongCreateRequest.SongVocalCreateRequest> vocals) {
        if (vocals == null || vocals.isEmpty()) {
            return List.of();
        }
        validateNoNullItems("vocals", vocals);

        List<UUID> vocalUuids =
                vocals.stream()
                        .map(SongCreateRequest.SongVocalCreateRequest::vocalResourceUuid)
                        .distinct()
                        .toList();
        Map<UUID, Long> vocalIdsByUuid = fetchVocalIdsByResourceUuid(vocalUuids);

        List<UUID> voicebankUuids =
                vocals.stream()
                        .map(SongCreateRequest.SongVocalCreateRequest::voicebankResourceUuid)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
        Map<UUID, Long> voicebankIdsByUuid = fetchVoicebankIdsByResourceUuid(voicebankUuids);

        List<SongVocal> entities =
                vocals.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "vocals contains null item");
                                    }
                                    Long vocalId = vocalIdsByUuid.get(item.vocalResourceUuid());
                                    if (vocalId == null) {
                                        throw new IllegalArgumentException(
                                                "Unknown vocalResourceUuid: "
                                                        + item.vocalResourceUuid());
                                    }
                                    VocalCharacter vocal =
                                            entityManager.getReference(
                                                    VocalCharacter.class, vocalId);

                                    VocalVoicebank voicebank = null;
                                    if (item.voicebankResourceUuid() != null) {
                                        Long voicebankId =
                                                voicebankIdsByUuid.get(
                                                        item.voicebankResourceUuid());
                                        if (voicebankId == null) {
                                            throw new IllegalArgumentException(
                                                    "Unknown voicebankResourceUuid: "
                                                            + item.voicebankResourceUuid());
                                        }
                                        voicebank =
                                                entityManager.getReference(
                                                        VocalVoicebank.class, voicebankId);
                                    }

                                    return SongVocal.create(
                                            song,
                                            vocal,
                                            voicebank,
                                            item.isMain(),
                                            item.sortOrder() == null ? 0 : item.sortOrder());
                                })
                        .toList();

        return songVocalRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(SongVocal::getSortOrder)
                                .thenComparing(SongVocal::getId))
                .toList();
    }

    private List<SongRelation> saveSongRelations(
            Song song, List<SongCreateRequest.SongRelationCreateRequest> relations) {
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        validateNoNullItems("relations", relations);

        HashSet<UUID> uniqueTargetSongUuids = new HashSet<>();
        for (SongCreateRequest.SongRelationCreateRequest relation : relations) {
            UUID targetSongResourceUuid = relation.targetSongResourceUuid();
            if (!uniqueTargetSongUuids.add(targetSongResourceUuid)) {
                throw new IllegalArgumentException(
                        "Duplicate targetSongResourceUuid: " + targetSongResourceUuid);
            }
        }

        List<UUID> relationUuids =
                relations.stream()
                        .map(SongCreateRequest.SongRelationCreateRequest::targetSongResourceUuid)
                        .distinct()
                        .toList();
        Map<UUID, Long> songIdsByUuid = fetchSongIdsByResourceUuid(relationUuids);
        UUID sourceSongResourceUuid = song.getResource().getUuid();

        List<SongRelation> entities =
                relations.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "relations contains null item");
                                    }
                                    if (sourceSongResourceUuid.equals(
                                            item.targetSongResourceUuid())) {
                                        throw new IllegalArgumentException(
                                                "sourceSong and targetSong must be different");
                                    }
                                    Long targetSongId =
                                            songIdsByUuid.get(item.targetSongResourceUuid());
                                    if (targetSongId == null) {
                                        throw new IllegalArgumentException(
                                                "Unknown targetSongResourceUuid: "
                                                        + item.targetSongResourceUuid());
                                    }
                                    Song targetSong =
                                            entityManager.getReference(Song.class, targetSongId);
                                    return SongRelation.create(song, targetSong);
                                })
                        .toList();

        return songRelationRepository.saveAllAndFlush(entities);
    }

    private Map<UUID, Long> fetchArtistIdsByResourceUuid(List<UUID> resourceUuids) {
        if (resourceUuids.isEmpty()) {
            return Map.of();
        }
        List<ResourceRefProjection> refs =
                artistRepository.findResourceRefsByResourceUuids(resourceUuids);
        return toIdMap(refs);
    }

    private Map<UUID, Long> fetchVocalIdsByResourceUuid(List<UUID> resourceUuids) {
        if (resourceUuids.isEmpty()) {
            return Map.of();
        }
        List<ResourceRefProjection> refs =
                vocalCharacterRepository.findResourceRefsByResourceUuids(resourceUuids);
        return toIdMap(refs);
    }

    private Map<UUID, Long> fetchVoicebankIdsByResourceUuid(List<UUID> resourceUuids) {
        if (resourceUuids.isEmpty()) {
            return Map.of();
        }
        List<ResourceRefProjection> refs =
                vocalVoicebankRepository.findResourceRefsByResourceUuids(resourceUuids);
        return toIdMap(refs);
    }

    private Map<UUID, Long> fetchSongIdsByResourceUuid(List<UUID> resourceUuids) {
        if (resourceUuids.isEmpty()) {
            return Map.of();
        }
        List<ResourceRefProjection> refs =
                songRepository.findResourceRefsByResourceUuids(resourceUuids);
        return toIdMap(refs);
    }

    private Map<UUID, Long> toIdMap(List<ResourceRefProjection> refs) {
        return refs.stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                ResourceRefProjection::getResourceUuid,
                                ResourceRefProjection::getId));
    }

    private String normalizeAclSubjectValue(AclSubjectType subjectType, String subjectValue) {
        if (subjectType == null) {
            throw new IllegalArgumentException("acl.subjectType is required");
        }

        String normalized = subjectValue == null ? "" : subjectValue.trim();
        return switch (subjectType) {
            case ANONYMOUS, USER, USER_15, USER_VERIFIED, ADMIN -> {
                if (!normalized.isEmpty()) {
                    throw new IllegalArgumentException(
                            "subjectValue must be empty for subjectType " + subjectType.name());
                }
                yield "";
            }
            case USER_ID, GUEST_ID, ACL_GROUP -> {
                if (normalized.isEmpty()) {
                    throw new IllegalArgumentException(
                            "subjectValue is required for subjectType " + subjectType.name());
                }
                yield normalized;
            }
        };
    }

    private SongType parseSongType(String value) {
        return parseEnum(value, SongType.class, "songType");
    }

    private SongPvProvider parseSongPvProvider(String value) {
        return parseEnum(value, SongPvProvider.class, "pvs.service");
    }

    private AclAction parseAclAction(String value) {
        return parseEnum(value, AclAction.class, "acls.action");
    }

    private AclSubjectType parseAclSubjectType(String value) {
        return parseEnum(value, AclSubjectType.class, "acls.subjectType");
    }

    private AclEffect parseAclEffect(String value) {
        if (value == null || value.isBlank()) {
            return AclEffect.ALLOW;
        }
        return parseEnum(value, AclEffect.class, "acls.effect");
    }

    private Set<SongArtistRole> parseSongArtistRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("artists.roles is required");
        }
        return roles.stream()
                .map(role -> parseEnum(role, SongArtistRole.class, "artists.roles"))
                .collect(java.util.stream.Collectors.toSet());
    }

    private <E extends Enum<E>> E parseEnum(String rawValue, Class<E> enumClass, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = rawValue.trim().toUpperCase(java.util.Locale.ROOT);
        try {
            return Enum.valueOf(enumClass, normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(fieldName + " is invalid: " + rawValue);
        }
    }

    private void validateNoNullItems(String fieldName, List<?> items) {
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(fieldName + " contains null item");
        }
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
    }

    private JsonNode toRequiredJsonNode(Object value, String fieldName) {
        JsonNode jsonNode = toJsonNode(value);
        if (jsonNode == null || jsonNode.isNull()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return jsonNode;
    }

    private JsonNode buildSongProjection(
            Song song,
            Resource resource,
            List<ResourceName> names,
            List<Acl> acls,
            List<SongLyric> lyrics,
            List<SongPv> pvs,
            List<SongArtist> artists,
            List<SongVocal> vocals,
            List<SongRelation> relations) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("resourceUuid", resource.getUuid().toString());
        data.put("canonicalName", resource.getCanonicalName());
        data.put("status", resource.getStatus().name());
        data.put("songType", song.getSongType().name());
        data.put("viewCount", resource.getViewCount());
        putNullableText(data, "thumbnailUrl", resource.getThumbnailUrl());
        putNullableText(data, "content", song.getContent());
        data.set(
                "links",
                song.getLinks() == null ? objectMapper.createArrayNode() : song.getLinks());
        putNullableText(data, "publishedAt", formatDateTime(song.getPublishedAt()));
        putNullableText(data, "createdAt", formatDateTime(resource.getCreatedAt()));
        putNullableText(data, "updatedAt", formatDateTime(resource.getUpdatedAt()));
        data.set("names", buildNamesProjection(names));
        data.set("acls", buildAclsProjection(acls));

        data.set("lyrics", buildSongLyricsProjection(lyrics));
        data.set("pvs", buildSongPvsProjection(pvs));
        data.set("artists", buildSongArtistsProjection(artists));
        data.set("vocals", buildSongVocalsProjection(vocals));
        data.set("relations", buildSongRelationsProjection(relations));
        data.set("incomingRelations", objectMapper.createArrayNode());
        data.set("playlists", objectMapper.createArrayNode());
        return data;
    }

    private ArrayNode buildNamesProjection(List<ResourceName> names) {
        ArrayNode items = objectMapper.createArrayNode();
        for (ResourceName name : names) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("nameUuid", name.getUuid().toString());
            item.put("langCode", name.getLangCode().name());
            item.put("name", name.getName());
            item.put("isPrimary", name.isPrimary());
            item.put("sortOrder", name.getSortOrder());
            putNullableText(item, "createdAt", formatDateTime(name.getCreatedAt()));
            putNullableText(item, "updatedAt", formatDateTime(name.getUpdatedAt()));
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildAclsProjection(List<Acl> acls) {
        ArrayNode items = objectMapper.createArrayNode();
        for (Acl acl : acls) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("aclUuid", acl.getUuid().toString());
            item.put("action", acl.getAction().name());
            item.put("subjectType", acl.getSubjectType().name());
            item.put("subjectValue", acl.getSubjectValue());
            item.put("effect", acl.getEffect().name());
            item.put("priority", acl.getPriority());
            putNullableText(item, "expiresAt", formatDateTime(acl.getExpiresAt()));
            putNullableText(item, "createdAt", formatDateTime(acl.getCreatedAt()));
            putNullableText(item, "updatedAt", formatDateTime(acl.getUpdatedAt()));
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildSongLyricsProjection(List<SongLyric> lyrics) {
        ArrayNode items = objectMapper.createArrayNode();
        for (SongLyric lyric : lyrics) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("lyricUuid", lyric.getUuid().toString());
            item.set(
                    "langCodes",
                    objectMapper.valueToTree(
                            lyric.getLangCodes().stream().map(Enum::name).sorted().toList()));
            item.set("lyrics", lyric.getLyrics());
            item.put("isPrimary", lyric.isPrimary());
            item.put("sortOrder", lyric.getSortOrder());
            putNullableText(item, "createdAt", formatDateTime(lyric.getCreatedAt()));
            putNullableText(item, "updatedAt", formatDateTime(lyric.getUpdatedAt()));
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildSongPvsProjection(List<SongPv> pvs) {
        ArrayNode items = objectMapper.createArrayNode();
        List<SongPv> sortedPvs =
                pvs.stream()
                        .sorted(
                                Comparator.comparingInt(SongPv::getSortOrder)
                                        .thenComparing(SongPv::getId))
                        .toList();
        for (SongPv pv : sortedPvs) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("pvUuid", pv.getUuid().toString());
            item.putNull("pvViewUuid");
            item.put("service", pv.getService().name());
            item.put("videoKey", pv.getVideoKey());
            putNullableText(item, "title", pv.getTitle());
            putNullableText(item, "thumbnailUrl", pv.getThumbnailUrl());
            putNullableText(item, "uploaderKey", pv.getUploaderKey());
            if (pv.getDurationSeconds() == null) {
                item.putNull("durationSeconds");
            } else {
                item.put("durationSeconds", pv.getDurationSeconds());
            }
            item.put("isOfficial", pv.isOfficial());
            putNullableText(item, "publishedAt", formatDateTime(pv.getPublishedAt()));
            item.put("sortOrder", pv.getSortOrder());
            item.put("viewCount", 0L);
            putNullableText(item, "createdAt", formatDateTime(pv.getCreatedAt()));
            putNullableText(item, "updatedAt", formatDateTime(pv.getUpdatedAt()));
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildSongArtistsProjection(List<SongArtist> artists) {
        ArrayNode items = objectMapper.createArrayNode();
        for (SongArtist songArtist : artists) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put(
                    "artistResourceUuid",
                    songArtist.getArtist().getResource().getUuid().toString());
            item.put("canonicalName", songArtist.getArtist().getResource().getCanonicalName());
            putNullableText(
                    item, "thumbnailUrl", songArtist.getArtist().getResource().getThumbnailUrl());
            item.put("isMain", songArtist.isMain());
            item.put("sortOrder", songArtist.getSortOrder());
            item.set(
                    "roles",
                    objectMapper.valueToTree(
                            songArtist.getRoles().stream()
                                    .map(SongArtistRole::name)
                                    .sorted()
                                    .toList()));
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildSongVocalsProjection(List<SongVocal> vocals) {
        ArrayNode items = objectMapper.createArrayNode();
        for (SongVocal songVocal : vocals) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("vocalResourceUuid", songVocal.getVocal().getResource().getUuid().toString());
            item.put("vocalCanonicalName", songVocal.getVocal().getResource().getCanonicalName());
            if (songVocal.getVoicebank() == null) {
                item.putNull("voicebankResourceUuid");
                item.putNull("voicebankCanonicalName");
                item.putNull("voicebankType");
            } else {
                item.put(
                        "voicebankResourceUuid",
                        songVocal.getVoicebank().getResource().getUuid().toString());
                item.put(
                        "voicebankCanonicalName",
                        songVocal.getVoicebank().getResource().getCanonicalName());
                item.put("voicebankType", songVocal.getVoicebank().getVoicebankType().name());
            }
            item.put("isMain", songVocal.isMain());
            item.put("sortOrder", songVocal.getSortOrder());
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildSongRelationsProjection(List<SongRelation> relations) {
        ArrayNode items = objectMapper.createArrayNode();
        for (SongRelation relation : relations) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put(
                    "targetSongResourceUuid",
                    relation.getTargetSong().getResource().getUuid().toString());
            item.put(
                    "targetSongCanonicalName",
                    relation.getTargetSong().getResource().getCanonicalName());
            item.put("targetSongType", relation.getTargetSong().getSongType().name());
            items.add(item);
        }
        return items;
    }

    private void putNullableText(ObjectNode node, String fieldName, String value) {
        if (value == null) {
            node.putNull(fieldName);
            return;
        }
        node.put(fieldName, value);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private SongElementResponse toSummary(Song song) {
        Resource resource = song.getResource();
        return new SongElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                song.getSongType().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                song.getPublishedAt(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
