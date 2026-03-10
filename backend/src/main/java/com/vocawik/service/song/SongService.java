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
import com.vocawik.domain.vocal.Vocal;
import com.vocawik.dto.song.SongCreateRequest;
import com.vocawik.dto.song.SongElementResponse;
import com.vocawik.dto.song.SongListResponse;
import com.vocawik.dto.song.SongUpdateRequest;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongCriteria;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongPvViewRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
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
    private final SongPvViewRepository songPvViewRepository;
    private final SongArtistRepository songArtistRepository;
    private final SongVocalRepository songVocalRepository;
    private final SongRelationRepository songRelationRepository;
    private final ArtistRepository artistRepository;
    private final VocalRepository vocalRepository;
    private final ResourceHistoryService resourceHistoryService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * Searches songs with optional filters.
     *
     * @param status optional resource status filter
     * @param songTypes optional song type filters
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
            List<SongType> songTypes,
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
                                songTypes,
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

        saveResourceNames(resource, request.names());
        saveAcls(resource, request.acls());
        saveSongLyrics(song, request.lyrics());
        saveSongPvs(song, request.pvs());
        saveSongArtists(song, request.artists());
        List<SongVocal> vocals = saveSongVocals(song, request.vocals());
        saveSongRelations(song, request.relations());
        validateSongParticipationPresent(vocals);

        resourceHistoryService.recordCreate(resource, buildHistorySnapshot(song, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Updates a song and optionally replaces child collections.
     *
     * @param resourceUuid song resource UUID
     * @param request update payload
     * @return updated song resource UUID
     */
    @Transactional
    public UUID update(UUID resourceUuid, SongUpdateRequest request) {
        Song song =
                songRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = song.getResource();
        updateSongFields(song, resource, request);

        if (request.names() != null) {
            replaceResourceNames(resource, toCreateNames(request.names()));
        }
        if (request.acls() != null) {
            replaceAcls(resource, toCreateAcls(request.acls()));
        }
        if (request.lyrics() != null) {
            replaceSongLyrics(song, toCreateLyrics(request.lyrics()));
        }
        if (request.pvs() != null) {
            replaceSongPvs(song, toCreatePvs(request.pvs()));
        }
        if (request.artists() != null) {
            replaceSongArtists(song, toCreateArtists(request.artists()));
        }
        List<SongVocal> vocals =
                request.vocals() == null
                        ? songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId())
                        : replaceSongVocals(song, toCreateVocals(request.vocals()));
        if (request.relations() != null) {
            replaceSongRelations(song, toCreateRelations(request.relations()));
        }
        validateSongParticipationPresent(vocals);

        resourceHistoryService.recordUpdate(resource, buildHistorySnapshot(song, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Soft-deletes a song and records delete history.
     *
     * @param resourceUuid song resource UUID
     */
    @Transactional
    public void delete(UUID resourceUuid) {
        Song song =
                songRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = song.getResource();
        JsonNode snapshot = buildHistorySnapshot(song, resource);

        resource.softDelete();
        resourceHistoryService.recordDelete(resource, snapshot);
        resourceRepository.saveAndFlush(resource);
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

    private void updateSongFields(Song song, Resource resource, SongUpdateRequest request) {
        String canonicalName =
                request.canonicalName() == null
                        ? resource.getCanonicalName()
                        : normalizeCanonicalName(request.canonicalName());
        String thumbnailUrl =
                request.thumbnailUrl() == null
                        ? resource.getThumbnailUrl()
                        : normalizeNullable(request.thumbnailUrl());
        String content =
                request.content() == null
                        ? song.getContent()
                        : normalizeNullable(request.content());
        JsonNode links = request.links() == null ? song.getLinks() : toJsonNode(request.links());
        validateLinks(links);
        LocalDateTime publishedAt =
                request.publishedAt() == null ? song.getPublishedAt() : request.publishedAt();
        SongType songType =
                request.songType() == null ? song.getSongType() : parseSongType(request.songType());

        resource.updateCanonicalName(canonicalName);
        resource.updateThumbnailUrl(thumbnailUrl);
        song.update(content, links, publishedAt, songType);
    }

    private List<ResourceName> replaceResourceNames(
            Resource resource, List<SongCreateRequest.ResourceNameCreateRequest> names) {
        resourceNameRepository.deleteByResourceId(resource.getId());
        return saveResourceNames(resource, names);
    }

    private List<Acl> replaceAcls(
            Resource resource, List<SongCreateRequest.ResourceAclCreateRequest> acls) {
        aclRepository.deleteByResourceId(resource.getId());
        return saveAcls(resource, acls);
    }

    private List<SongLyric> replaceSongLyrics(
            Song song, List<SongCreateRequest.SongLyricCreateRequest> lyrics) {
        songLyricRepository.deleteBySongId(song.getId());
        return saveSongLyrics(song, lyrics);
    }

    private List<SongPv> replaceSongPvs(
            Song song, List<SongCreateRequest.SongPvCreateRequest> pvs) {
        List<Long> existingSongPvIds = songPvRepository.findIdsBySongId(song.getId());
        if (!existingSongPvIds.isEmpty()) {
            songPvViewRepository.deleteBySongPvIds(existingSongPvIds);
        }
        songPvRepository.deleteBySongId(song.getId());
        return saveSongPvs(song, pvs);
    }

    private List<SongArtist> replaceSongArtists(
            Song song, List<SongCreateRequest.SongArtistCreateRequest> artists) {
        songArtistRepository.deleteBySongId(song.getId());
        return saveSongArtists(song, artists);
    }

    private List<SongVocal> replaceSongVocals(
            Song song, List<SongCreateRequest.SongVocalCreateRequest> vocals) {
        songVocalRepository.deleteBySongId(song.getId());
        return saveSongVocals(song, vocals);
    }

    private List<SongRelation> replaceSongRelations(
            Song song, List<SongCreateRequest.SongRelationCreateRequest> relations) {
        songRelationRepository.deleteBySourceSongId(song.getId());
        return saveSongRelations(song, relations);
    }

    private List<SongCreateRequest.ResourceNameCreateRequest> toCreateNames(
            List<SongUpdateRequest.ResourceNameUpdateRequest> names) {
        return names.stream()
                .map(
                        item ->
                                new SongCreateRequest.ResourceNameCreateRequest(
                                        item.langCode(),
                                        item.name(),
                                        item.isPrimary(),
                                        item.sortOrder()))
                .toList();
    }

    private List<SongCreateRequest.ResourceAclCreateRequest> toCreateAcls(
            List<SongUpdateRequest.ResourceAclUpdateRequest> acls) {
        return acls.stream()
                .map(
                        item ->
                                new SongCreateRequest.ResourceAclCreateRequest(
                                        item.action(),
                                        item.subjectType(),
                                        item.subjectValue(),
                                        item.effect(),
                                        item.priority(),
                                        item.expiresAt()))
                .toList();
    }

    private List<SongCreateRequest.SongLyricCreateRequest> toCreateLyrics(
            List<SongUpdateRequest.SongLyricUpdateRequest> lyrics) {
        return lyrics.stream()
                .map(
                        item ->
                                new SongCreateRequest.SongLyricCreateRequest(
                                        item.langCodes(),
                                        item.lyrics(),
                                        item.isPrimary(),
                                        item.sortOrder()))
                .toList();
    }

    private List<SongCreateRequest.SongPvCreateRequest> toCreatePvs(
            List<SongUpdateRequest.SongPvUpdateRequest> pvs) {
        return pvs.stream()
                .map(
                        item ->
                                new SongCreateRequest.SongPvCreateRequest(
                                        item.service(),
                                        item.videoKey(),
                                        item.title(),
                                        item.thumbnailUrl(),
                                        item.uploaderKey(),
                                        item.durationSeconds(),
                                        item.isOfficial(),
                                        item.publishedAt(),
                                        item.sortOrder()))
                .toList();
    }

    private List<SongCreateRequest.SongArtistCreateRequest> toCreateArtists(
            List<SongUpdateRequest.SongArtistUpdateRequest> artists) {
        return artists.stream()
                .map(
                        item ->
                                new SongCreateRequest.SongArtistCreateRequest(
                                        item.artistResourceUuid(),
                                        item.roles(),
                                        item.isMain(),
                                        item.sortOrder()))
                .toList();
    }

    private List<SongCreateRequest.SongVocalCreateRequest> toCreateVocals(
            List<SongUpdateRequest.SongVocalUpdateRequest> vocals) {
        return vocals.stream()
                .map(
                        item ->
                                new SongCreateRequest.SongVocalCreateRequest(
                                        item.vocalResourceUuid(), item.isMain(), item.sortOrder()))
                .toList();
    }

    private List<SongCreateRequest.SongRelationCreateRequest> toCreateRelations(
            List<SongUpdateRequest.SongRelationUpdateRequest> relations) {
        return relations.stream()
                .map(
                        item ->
                                new SongCreateRequest.SongRelationCreateRequest(
                                        item.targetSongResourceUuid()))
                .toList();
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
                                    Vocal vocal = entityManager.getReference(Vocal.class, vocalId);
                                    return SongVocal.create(
                                            song,
                                            vocal,
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
                vocalRepository.findResourceRefsByResourceUuids(resourceUuids);
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

    private void validateSongParticipationPresent(List<SongVocal> vocals) {
        if (vocals == null || vocals.isEmpty()) {
            throw new IllegalArgumentException("At least one vocal is required");
        }
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

    private JsonNode buildHistorySnapshot(Song song, Resource resource) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("canonicalName", resource.getCanonicalName());
        if (resource.getThumbnailUrl() == null) {
            snapshot.putNull("thumbnailUrl");
        } else {
            snapshot.put("thumbnailUrl", resource.getThumbnailUrl());
        }
        if (song.getContent() == null) {
            snapshot.putNull("content");
        } else {
            snapshot.put("content", song.getContent());
        }
        snapshot.set("links", toSnapshotJson(song.getLinks()));
        if (song.getPublishedAt() == null) {
            snapshot.putNull("publishedAt");
        } else {
            snapshot.put("publishedAt", song.getPublishedAt().toString());
        }
        snapshot.put("songType", song.getSongType().name());
        snapshot.set("names", buildNamesSnapshot(resource));
        snapshot.set("acls", buildAclsSnapshot(resource));
        snapshot.set("lyrics", buildLyricsSnapshot(song));
        snapshot.set("pvs", buildPvsSnapshot(song));
        snapshot.set("artists", buildArtistsSnapshot(song));
        snapshot.set("vocals", buildVocalsSnapshot(song));
        snapshot.set("relations", buildRelationsSnapshot(song));
        return snapshot;
    }

    private ArrayNode buildNamesSnapshot(Resource resource) {
        ArrayNode names = objectMapper.createArrayNode();
        for (ResourceName item :
                resourceNameRepository.findAllByResourceIdOrderBySortOrderAscIdAsc(
                        resource.getId())) {
            ObjectNode name = objectMapper.createObjectNode();
            name.put("langCode", item.getLangCode().name());
            name.put("name", item.getName());
            name.put("isPrimary", item.isPrimary());
            name.put("sortOrder", item.getSortOrder());
            names.add(name);
        }
        return names;
    }

    private ArrayNode buildAclsSnapshot(Resource resource) {
        ArrayNode acls = objectMapper.createArrayNode();
        for (Acl item :
                aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resource.getId())) {
            ObjectNode acl = objectMapper.createObjectNode();
            acl.put("action", item.getAction().name());
            acl.put("subjectType", item.getSubjectType().name());
            acl.put("subjectValue", item.getSubjectValue());
            acl.put("effect", item.getEffect().name());
            acl.put("priority", item.getPriority());
            if (item.getExpiresAt() == null) {
                acl.putNull("expiresAt");
            } else {
                acl.put("expiresAt", item.getExpiresAt().toString());
            }
            acls.add(acl);
        }
        return acls;
    }

    private ArrayNode buildLyricsSnapshot(Song song) {
        ArrayNode lyrics = objectMapper.createArrayNode();
        for (SongLyric item :
                songLyricRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId())) {
            ObjectNode lyric = objectMapper.createObjectNode();
            ArrayNode langCodes = objectMapper.createArrayNode();
            item.getLangCodes().stream().map(Enum::name).sorted().forEach(langCodes::add);
            lyric.set("langCodes", langCodes);
            lyric.set("lyrics", toSnapshotJson(item.getLyrics()));
            lyric.put("isPrimary", item.isPrimary());
            lyric.put("sortOrder", item.getSortOrder());
            lyrics.add(lyric);
        }
        return lyrics;
    }

    private ArrayNode buildPvsSnapshot(Song song) {
        ArrayNode pvs = objectMapper.createArrayNode();
        for (SongPv item : songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId())) {
            ObjectNode pv = objectMapper.createObjectNode();
            pv.put("service", item.getService().name());
            pv.put("videoKey", item.getVideoKey());
            if (item.getTitle() == null) {
                pv.putNull("title");
            } else {
                pv.put("title", item.getTitle());
            }
            if (item.getThumbnailUrl() == null) {
                pv.putNull("thumbnailUrl");
            } else {
                pv.put("thumbnailUrl", item.getThumbnailUrl());
            }
            if (item.getUploaderKey() == null) {
                pv.putNull("uploaderKey");
            } else {
                pv.put("uploaderKey", item.getUploaderKey());
            }
            if (item.getDurationSeconds() == null) {
                pv.putNull("durationSeconds");
            } else {
                pv.put("durationSeconds", item.getDurationSeconds());
            }
            pv.put("isOfficial", item.isOfficial());
            if (item.getPublishedAt() == null) {
                pv.putNull("publishedAt");
            } else {
                pv.put("publishedAt", item.getPublishedAt().toString());
            }
            pv.put("sortOrder", item.getSortOrder());
            pvs.add(pv);
        }
        return pvs;
    }

    private ArrayNode buildArtistsSnapshot(Song song) {
        ArrayNode artists = objectMapper.createArrayNode();
        for (SongArtist item :
                songArtistRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId())) {
            ObjectNode artist = objectMapper.createObjectNode();
            artist.put("artistResourceUuid", item.getArtist().getResource().getUuid().toString());
            ArrayNode roles = objectMapper.createArrayNode();
            item.getRoles().stream().map(Enum::name).sorted().forEach(roles::add);
            artist.set("roles", roles);
            artist.put("isMain", item.isMain());
            artist.put("sortOrder", item.getSortOrder());
            artists.add(artist);
        }
        return artists;
    }

    private ArrayNode buildVocalsSnapshot(Song song) {
        ArrayNode vocals = objectMapper.createArrayNode();
        for (SongVocal item :
                songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId())) {
            ObjectNode vocal = objectMapper.createObjectNode();
            vocal.put("vocalResourceUuid", item.getVocal().getResource().getUuid().toString());
            vocal.put("isMain", item.isMain());
            vocal.put("sortOrder", item.getSortOrder());
            vocals.add(vocal);
        }
        return vocals;
    }

    private ArrayNode buildRelationsSnapshot(Song song) {
        ArrayNode relations = objectMapper.createArrayNode();
        for (SongRelation item :
                songRelationRepository.findAllBySourceSongIdOrderByIdAsc(song.getId())) {
            ObjectNode relation = objectMapper.createObjectNode();
            relation.put(
                    "targetSongResourceUuid",
                    item.getTargetSong().getResource().getUuid().toString());
            relations.add(relation);
        }
        return relations;
    }

    private JsonNode toSnapshotJson(JsonNode value) {
        return value == null ? objectMapper.nullNode() : value.deepCopy();
    }
}
