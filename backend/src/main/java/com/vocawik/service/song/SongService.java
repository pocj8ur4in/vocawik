package com.vocawik.service.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vocawik.common.i18n.Language;
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
import com.vocawik.domain.song.SongLink;
import com.vocawik.domain.song.SongLinkType;
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
import com.vocawik.dto.song.SongPvResolveRequest;
import com.vocawik.dto.song.SongPvResolveResponse;
import com.vocawik.dto.song.SongSuggestionElementResponse;
import com.vocawik.dto.song.SongSuggestionListResponse;
import com.vocawik.dto.song.SongUpdateRequest;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongCriteria;
import com.vocawik.repository.song.SongLinkRepository;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongPvViewRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.service.pv.client.PvMetaApiClient;
import com.vocawik.service.pv.client.PvMetaApiClientResolver;
import com.vocawik.service.pv.detector.PvUrlDetector;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private static final int SONG_SUGGESTION_LIMIT = 10;

    private final SongRepository songRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final SongLinkRepository songLinkRepository;
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
    private final PvUrlDetector pvUrlDetector;
    private final PvMetaApiClientResolver pvMetaApiClientResolver;

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

        Page<Song> result =
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

        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceId(result.getContent());
        List<SongElementResponse> items =
                result.getContent().stream()
                        .map(song -> toSummary(song, localizedNamesByResourceId))
                        .toList();

        return new SongListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public SongSuggestionListResponse suggest(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new SongSuggestionListResponse(List.of());
        }

        LinkedHashMap<String, LinkedHashSet<UUID>> resourceUuidsByName = new LinkedHashMap<>();
        resourceNameRepository
                .findSongSuggestionCandidates(
                        ResourceStatus.ACTIVE,
                        normalizedQuery,
                        org.springframework.data.domain.PageRequest.of(
                                0, SONG_SUGGESTION_LIMIT * 3))
                .forEach(
                        resourceName -> {
                            resourceUuidsByName
                                    .computeIfAbsent(
                                            resourceName.getName(),
                                            ignored -> new LinkedHashSet<>())
                                    .add(resourceName.getResource().getUuid());
                        });

        return new SongSuggestionListResponse(
                resourceUuidsByName.entrySet().stream()
                        .limit(SONG_SUGGESTION_LIMIT)
                        .map(
                                entry -> {
                                    boolean hasMultipleResources = entry.getValue().size() > 1;
                                    UUID resourceUuid =
                                            hasMultipleResources
                                                    ? null
                                                    : entry.getValue().iterator().next();
                                    return new SongSuggestionElementResponse(
                                            resourceUuid, entry.getKey(), hasMultipleResources);
                                })
                        .toList());
    }

    /**
     * Resolves song PV metadata from URL.
     *
     * @param request pv resolve request
     * @return resolved pv metadata
     */
    public SongPvResolveResponse resolveSongPv(SongPvResolveRequest request) {
        String url = request == null ? null : normalizeNullable(request.url());
        if (url == null) {
            throw new IllegalArgumentException("url is required");
        }
        DetectedPv detectedPv =
                pvUrlDetector
                        .detect(url)
                        .orElse(new DetectedPv(SongPvProvider.OTHER, "unknown", url));
        return pvMetaApiClientResolver
                .resolve(detectedPv.provider())
                .map(client -> toResolveResponse(detectedPv, client.fetch(detectedPv)))
                .orElseGet(() -> toFallbackResponse(detectedPv));
    }

    private SongPvResolveResponse toResolveResponse(
            DetectedPv detectedPv, PvMetaApiClient.PvMetaResult metaResult) {
        String videoKey = normalizeNullable(metaResult.videoKey());
        if (videoKey == null) {
            videoKey = detectedPv.videoKey();
        }
        boolean isDuplicated = isDuplicatedPv(detectedPv.provider(), videoKey);

        return new SongPvResolveResponse(
                detectedPv.provider().name(),
                videoKey,
                normalizeNullable(metaResult.title()),
                normalizeNullable(metaResult.thumbnailUrl()),
                normalizeNullable(metaResult.uploaderKey()),
                metaResult.durationSeconds(),
                normalizeNullable(metaResult.publishedAt()),
                isDuplicated,
                toResolveExtra(metaResult.extra()));
    }

    private SongPvResolveResponse toFallbackResponse(DetectedPv detectedPv) {
        boolean isDuplicated = isDuplicatedPv(detectedPv.provider(), detectedPv.videoKey());
        return new SongPvResolveResponse(
                detectedPv.provider().name(),
                detectedPv.videoKey(),
                null,
                null,
                null,
                null,
                null,
                isDuplicated,
                null);
    }

    private SongPvResolveResponse.SongPvResolveExtra toResolveExtra(
            PvMetaApiClient.PvMetaExtra extra) {
        if (extra == null) {
            return null;
        }

        String audioUrl = normalizeNullable(extra.audioUrl());
        Long cid = extra.cid();
        String externalUrl = normalizeNullable(extra.externalUrl());
        if (audioUrl == null && cid == null && externalUrl == null) {
            return null;
        }
        return new SongPvResolveResponse.SongPvResolveExtra(audioUrl, cid, externalUrl);
    }

    private boolean isDuplicatedPv(SongPvProvider provider, String videoKey) {
        if (provider == null || provider == SongPvProvider.OTHER) {
            return false;
        }
        String normalizedVideoKey = normalizeNullable(videoKey);
        if (normalizedVideoKey == null) {
            return false;
        }
        return songPvRepository.existsByServiceAndVideoKey(provider, normalizedVideoKey);
    }

    /**
     * Creates a song and initializes resource projection payload.
     *
     * @param request create payload
     * @return created song resource UUID
     */
    @Transactional
    public UUID create(SongCreateRequest request) {
        SongCreateRequest.CanonicalNameCreateRequest canonicalName = request.canonicalName();
        Song song =
                Song.create(
                        normalizeCanonicalName(canonicalName.name()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()),
                        request.publishedAt(),
                        parseSongType(request.songType()));

        Resource resource = resourceRepository.save(song.getResource());
        songRepository.save(song);

        saveResourceNames(resource, canonicalName, request.aliases());
        saveAcls(resource, request.acls());
        saveSongLinks(song, request.links());
        saveSongLyrics(song, request.lyrics());
        saveSongPvs(song, request.pvs());
        saveSongArtists(song, request.artists());
        List<SongVocal> vocals = saveSongVocals(song, request.vocals());
        saveSongRelation(song, request.relationsTargetSongResourceUuid());
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

        if (request.canonicalName() != null || request.aliases() != null) {
            SongCreateRequest.CanonicalNameCreateRequest canonicalName =
                    request.canonicalName() == null
                            ? toCreateCanonical(loadCanonicalName(resource))
                            : toCreateCanonical(request.canonicalName());
            List<SongCreateRequest.ResourceAliasCreateRequest> aliases =
                    request.aliases() == null
                            ? toCreateAliasesFromResourceNames(loadAliases(resource))
                            : toCreateAliases(request.aliases());
            syncResourceNames(resource, canonicalName, aliases);
        }
        if (request.acls() != null) {
            syncAcls(resource, toCreateAcls(request.acls()));
        }
        if (request.links() != null) {
            syncSongLinks(song, request.links());
        }
        if (request.lyrics() != null) {
            syncSongLyrics(song, toCreateLyrics(request.lyrics()));
        }
        if (request.pvs() != null) {
            syncSongPvs(song, toCreatePvs(request.pvs()));
        }
        if (request.artists() != null) {
            syncSongArtists(song, toCreateArtists(request.artists()));
        }
        List<SongVocal> vocals =
                request.vocals() == null
                        ? songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId())
                        : syncSongVocals(song, toCreateVocals(request.vocals()));
        if (request.relationsTargetSongResourceUuid() != null) {
            syncSongRelation(song, request.relationsTargetSongResourceUuid());
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

    private void updateSongFields(Song song, Resource resource, SongUpdateRequest request) {
        String canonicalName =
                request.canonicalName() == null
                        ? resource.getCanonicalName()
                        : normalizeCanonicalName(request.canonicalName().name());
        String thumbnailUrl =
                request.thumbnailUrl() == null
                        ? resource.getThumbnailUrl()
                        : normalizeNullable(request.thumbnailUrl());
        String content =
                request.content() == null
                        ? song.getContent()
                        : normalizeNullable(request.content());
        LocalDateTime publishedAt =
                request.publishedAt() == null ? song.getPublishedAt() : request.publishedAt();
        SongType songType =
                request.songType() == null ? song.getSongType() : parseSongType(request.songType());

        resource.updateCanonicalName(canonicalName);
        resource.updateThumbnailUrl(thumbnailUrl);
        song.update(content, publishedAt, songType);
    }

    private List<ResourceName> syncResourceNames(
            Resource resource,
            SongCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<SongCreateRequest.ResourceAliasCreateRequest> aliases) {
        List<ResourceName> existingNames =
                resourceNameRepository.findAllByResourceIdOrderBySortOrderAscIdAsc(
                        resource.getId());

        HashSet<String> uniqueNames = new HashSet<>();
        String normalizedCanonicalName = normalizeCanonicalName(canonicalName.name());
        List<DesiredResourceName> desiredNames = new ArrayList<>();
        desiredNames.add(
                new DesiredResourceName(
                        canonicalName.langCode(), normalizedCanonicalName, true, 0));
        uniqueNames.add(canonicalName.langCode().name() + "|" + normalizedCanonicalName);

        for (SongCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<SongCreateRequest.ResourceAliasCreateRequest>of()
                        : aliases)) {
            if (alias == null) {
                throw new IllegalArgumentException("aliases contains null item");
            }
            String normalizedAlias = normalizeCanonicalName(alias.name());
            String uniqueKey = alias.langCode().name() + "|" + normalizedAlias;
            if (!uniqueNames.add(uniqueKey)) {
                throw new IllegalArgumentException(
                        "Duplicate resource name for language and value");
            }
            desiredNames.add(
                    new DesiredResourceName(
                            alias.langCode(),
                            normalizedAlias,
                            false,
                            alias.sortOrder() == null ? 0 : alias.sortOrder()));
        }

        Map<ResourceNameKey, ResourceName> existingByKey = new HashMap<>();
        for (ResourceName existing : existingNames) {
            existingByKey.put(
                    new ResourceNameKey(existing.getLangCode(), existing.getName()), existing);
        }

        List<ResourceName> toCreate = new ArrayList<>();
        for (DesiredResourceName desired : desiredNames) {
            ResourceNameKey key = new ResourceNameKey(desired.langCode(), desired.name());
            ResourceName existing = existingByKey.remove(key);
            if (existing == null) {
                toCreate.add(
                        ResourceName.create(
                                resource,
                                desired.langCode(),
                                desired.name(),
                                desired.isPrimary(),
                                desired.sortOrder()));
                continue;
            }
            existing.updateDisplay(desired.isPrimary(), desired.sortOrder());
        }

        if (!existingByKey.isEmpty()) {
            resourceNameRepository.deleteAllInBatch(new ArrayList<>(existingByKey.values()));
        }
        if (!toCreate.isEmpty()) {
            resourceNameRepository.saveAll(toCreate);
        }
        resourceNameRepository.flush();
        return resourceNameRepository
                .findAllByResourceIdOrderBySortOrderAscIdAsc(resource.getId())
                .stream()
                .sorted(
                        Comparator.comparingInt(ResourceName::getSortOrder)
                                .thenComparing(ResourceName::getId))
                .toList();
    }

    private List<Acl> syncAcls(
            Resource resource, List<SongCreateRequest.ResourceAclCreateRequest> acls) {
        List<Acl> existingAcls =
                aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resource.getId());
        if (acls.isEmpty()) {
            if (!existingAcls.isEmpty()) {
                aclRepository.deleteAllInBatch(existingAcls);
                aclRepository.flush();
            }
            return List.of();
        }

        Map<AclKey, Acl> existingByKey = new HashMap<>();
        for (Acl existing : existingAcls) {
            existingByKey.put(
                    new AclKey(
                            existing.getAction(),
                            existing.getSubjectType(),
                            existing.getSubjectValue(),
                            existing.getPriority()),
                    existing);
        }

        HashSet<AclKey> uniqueKeys = new HashSet<>();
        List<Acl> toCreate = new ArrayList<>();
        for (SongCreateRequest.ResourceAclCreateRequest item : acls) {
            if (item == null) {
                throw new IllegalArgumentException("acls contains null item");
            }
            AclAction action = parseAclAction(item.action());
            AclSubjectType subjectType = parseAclSubjectType(item.subjectType());
            String subjectValue = normalizeAclSubjectValue(subjectType, item.subjectValue());
            int priority = item.priority() == null ? 100 : item.priority();
            AclKey key = new AclKey(action, subjectType, subjectValue, priority);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate ACL for action/subject/priority combination");
            }

            AclEffect effect = parseAclEffect(item.effect());
            Acl existing = existingByKey.remove(key);
            if (existing == null) {
                toCreate.add(
                        Acl.create(
                                resource,
                                action,
                                subjectType,
                                subjectValue,
                                effect,
                                priority,
                                item.expiresAt()));
                continue;
            }
            existing.updateRule(effect, item.expiresAt());
        }

        if (!existingByKey.isEmpty()) {
            aclRepository.deleteAllInBatch(new ArrayList<>(existingByKey.values()));
        }
        if (!toCreate.isEmpty()) {
            aclRepository.saveAll(toCreate);
        }
        aclRepository.flush();
        return aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resource.getId());
    }

    private List<SongLyric> syncSongLyrics(
            Song song, List<SongCreateRequest.SongLyricCreateRequest> lyrics) {
        List<SongLyric> existingLyrics =
                songLyricRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        if (lyrics.isEmpty()) {
            if (!existingLyrics.isEmpty()) {
                songLyricRepository.deleteAllInBatch(existingLyrics);
                songLyricRepository.flush();
            }
            return List.of();
        }

        Map<SongLyricKey, Deque<SongLyric>> existingByKey = new HashMap<>();
        for (SongLyric existing : existingLyrics) {
            SongLyricKey key =
                    new SongLyricKey(
                            lyricLangCodesKey(existing.getLangCodes()),
                            existing.isPrimary(),
                            existing.getSortOrder());
            existingByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(existing);
        }

        HashSet<SongLyricKey> uniqueKeys = new HashSet<>();
        HashSet<Long> matchedIds = new HashSet<>();
        List<SongLyric> toCreate = new ArrayList<>();
        for (SongCreateRequest.SongLyricCreateRequest item : lyrics) {
            if (item == null) {
                throw new IllegalArgumentException("lyrics contains null item");
            }
            Set<Language> langCodes = item.langCodes();
            JsonNode lyricJson = toRequiredJsonNode(item.lyrics(), "lyrics.lyrics");
            int sortOrder = item.sortOrder() == null ? 0 : item.sortOrder();
            SongLyricKey key =
                    new SongLyricKey(lyricLangCodesKey(langCodes), item.isPrimary(), sortOrder);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate lyric for langCodes/isPrimary/sortOrder combination");
            }

            Deque<SongLyric> candidates = existingByKey.get(key);
            if (candidates != null && !candidates.isEmpty()) {
                SongLyric matched = candidates.removeFirst();
                matched.updateLangCodes(langCodes);
                matched.updateLyrics(lyricJson);
                matched.updateDisplay(item.isPrimary(), sortOrder);
                matchedIds.add(matched.getId());
                continue;
            }

            toCreate.add(SongLyric.create(song, langCodes, lyricJson, item.isPrimary(), sortOrder));
        }

        List<SongLyric> toDelete =
                existingLyrics.stream().filter(item -> !matchedIds.contains(item.getId())).toList();
        if (!toDelete.isEmpty()) {
            songLyricRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            songLyricRepository.saveAll(toCreate);
        }
        songLyricRepository.flush();
        return songLyricRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
    }

    private List<SongLink> syncSongLinks(
            Song song, List<SongUpdateRequest.SongLinkUpdateRequest> links) {
        List<SongLink> existingLinks = songLinkRepository.findAllBySongIdOrderByIdAsc(song.getId());
        if (links.isEmpty()) {
            if (!existingLinks.isEmpty()) {
                songLinkRepository.deleteAllInBatch(existingLinks);
                songLinkRepository.flush();
            }
            return List.of();
        }

        Map<SongLinkKey, Deque<SongLink>> existingByKey = new HashMap<>();
        for (SongLink existing : existingLinks) {
            SongLinkKey key = new SongLinkKey(existing.getSongLinkType(), existing.getUrl());
            existingByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(existing);
        }

        HashSet<Long> matchedIds = new HashSet<>();
        List<SongLink> toCreate = new ArrayList<>();
        for (SongUpdateRequest.SongLinkUpdateRequest item : links) {
            if (item == null) {
                throw new IllegalArgumentException("links contains null item");
            }
            SongLinkType type = parseSongLinkType(item.type());
            String url = normalizeLinkUrl(item.url());
            SongLinkKey key = new SongLinkKey(type, url);
            Deque<SongLink> candidates = existingByKey.get(key);

            if (candidates != null && !candidates.isEmpty()) {
                SongLink matched = candidates.removeFirst();
                matched.update(normalizeNullable(item.content()), item.isDeleted());
                matchedIds.add(matched.getId());
                continue;
            }

            toCreate.add(
                    SongLink.create(
                            song, type, url, normalizeNullable(item.content()), item.isDeleted()));
        }

        List<SongLink> toDelete =
                existingLinks.stream().filter(item -> !matchedIds.contains(item.getId())).toList();
        if (!toDelete.isEmpty()) {
            songLinkRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            songLinkRepository.saveAll(toCreate);
        }
        songLinkRepository.flush();
        return songLinkRepository.findAllBySongIdOrderByIdAsc(song.getId());
    }

    private List<SongPv> syncSongPvs(Song song, List<SongCreateRequest.SongPvCreateRequest> pvs) {
        List<SongPv> existingPvs =
                songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        if (pvs.isEmpty()) {
            List<Long> existingIds = existingPvs.stream().map(SongPv::getId).toList();
            if (!existingIds.isEmpty()) {
                songPvViewRepository.deleteBySongPvIds(existingIds);
                songPvRepository.deleteAllInBatch(existingPvs);
                songPvRepository.flush();
            }
            return List.of();
        }

        Map<SongPvKey, Deque<SongPv>> existingByKey = new HashMap<>();
        for (SongPv existing : existingPvs) {
            SongPvKey key = new SongPvKey(existing.getService(), existing.getVideoKey());
            existingByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(existing);
        }

        HashSet<SongPvKey> uniqueKeys = new HashSet<>();
        HashSet<Long> matchedIds = new HashSet<>();
        List<SongPv> toCreate = new ArrayList<>();
        for (SongCreateRequest.SongPvCreateRequest item : pvs) {
            if (item == null) {
                throw new IllegalArgumentException("pvs contains null item");
            }
            SongPvProvider service = parseSongPvProvider(item.service());
            String videoKey = normalizeRequired(item.videoKey(), "videoKey");
            SongPvKey key = new SongPvKey(service, videoKey);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate pvs.service + pvs.videoKey");
            }

            String title = normalizeNullable(item.title());
            String thumbnailUrl = normalizeNullable(item.thumbnailUrl());
            String uploaderKey = normalizeNullable(item.uploaderKey());
            SongPvExtraValues extra = normalizeSongPvExtra(service, item.extra());
            int sortOrder = item.sortOrder() == null ? 0 : item.sortOrder();

            Deque<SongPv> candidates = existingByKey.get(key);
            if (candidates != null && !candidates.isEmpty()) {
                SongPv matched = candidates.removeFirst();
                matched.updateMetadata(
                        title,
                        thumbnailUrl,
                        uploaderKey,
                        item.durationSeconds(),
                        item.isOfficial(),
                        item.publishedAt(),
                        extra.audioUrl(),
                        extra.cid(),
                        extra.externalUrl(),
                        sortOrder);
                matchedIds.add(matched.getId());
                continue;
            }

            toCreate.add(
                    SongPv.create(
                            song,
                            service,
                            videoKey,
                            title,
                            thumbnailUrl,
                            uploaderKey,
                            item.durationSeconds(),
                            item.isOfficial(),
                            item.publishedAt(),
                            extra.audioUrl(),
                            extra.cid(),
                            extra.externalUrl(),
                            sortOrder));
        }

        List<SongPv> toDelete =
                existingPvs.stream().filter(item -> !matchedIds.contains(item.getId())).toList();
        if (!toDelete.isEmpty()) {
            List<Long> toDeleteIds = toDelete.stream().map(SongPv::getId).toList();
            songPvViewRepository.deleteBySongPvIds(toDeleteIds);
            songPvRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            songPvRepository.saveAll(toCreate);
        }
        songPvRepository.flush();
        return songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
    }

    private List<SongArtist> syncSongArtists(
            Song song, List<SongCreateRequest.SongArtistCreateRequest> artists) {
        List<SongArtist> existingArtists =
                songArtistRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        if (artists.isEmpty()) {
            if (!existingArtists.isEmpty()) {
                songArtistRepository.deleteAllInBatch(existingArtists);
                songArtistRepository.flush();
            }
            return List.of();
        }

        validateNoNullItems("artists", artists);
        List<UUID> artistUuids =
                artists.stream()
                        .map(SongCreateRequest.SongArtistCreateRequest::artistResourceUuid)
                        .distinct()
                        .toList();
        Map<UUID, Long> artistIdsByUuid = fetchArtistIdsByResourceUuid(artistUuids);

        Map<SongArtistKey, Deque<SongArtist>> existingByKey = new HashMap<>();
        for (SongArtist existing : existingArtists) {
            SongArtistKey key =
                    new SongArtistKey(
                            existing.getArtist().getId(), songArtistRolesKey(existing.getRoles()));
            existingByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(existing);
        }

        HashSet<SongArtistKey> uniqueKeys = new HashSet<>();
        HashSet<Long> matchedIds = new HashSet<>();
        List<SongArtist> toCreate = new ArrayList<>();
        for (SongCreateRequest.SongArtistCreateRequest item : artists) {
            if (item == null) {
                throw new IllegalArgumentException("artists contains null item");
            }
            Long artistId = artistIdsByUuid.get(item.artistResourceUuid());
            if (artistId == null) {
                throw new IllegalArgumentException(
                        "Unknown artistResourceUuid: " + item.artistResourceUuid());
            }
            Set<SongArtistRole> roles = parseSongArtistRoles(item.roles());
            SongArtistKey key = new SongArtistKey(artistId, songArtistRolesKey(roles));
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate song artist for artistResourceUuid + roles");
            }

            int sortOrder = item.sortOrder() == null ? 0 : item.sortOrder();
            Deque<SongArtist> candidates = existingByKey.get(key);
            if (candidates != null && !candidates.isEmpty()) {
                SongArtist matched = candidates.removeFirst();
                matched.updateParticipation(item.isMain(), sortOrder);
                matchedIds.add(matched.getId());
                continue;
            }

            Artist artist = entityManager.getReference(Artist.class, artistId);
            toCreate.add(SongArtist.create(song, artist, roles, item.isMain(), sortOrder));
        }

        List<SongArtist> toDelete =
                existingArtists.stream()
                        .filter(item -> !matchedIds.contains(item.getId()))
                        .toList();
        if (!toDelete.isEmpty()) {
            songArtistRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            songArtistRepository.saveAll(toCreate);
        }
        songArtistRepository.flush();
        return songArtistRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
    }

    private List<SongVocal> syncSongVocals(
            Song song, List<SongCreateRequest.SongVocalCreateRequest> vocals) {
        List<SongVocal> existingVocals =
                songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
        if (vocals.isEmpty()) {
            if (!existingVocals.isEmpty()) {
                songVocalRepository.deleteAllInBatch(existingVocals);
                songVocalRepository.flush();
            }
            return List.of();
        }

        validateNoNullItems("vocals", vocals);
        List<UUID> vocalUuids =
                vocals.stream()
                        .map(SongCreateRequest.SongVocalCreateRequest::vocalResourceUuid)
                        .distinct()
                        .toList();
        Map<UUID, Long> vocalIdsByUuid = fetchVocalIdsByResourceUuid(vocalUuids);

        Map<SongVocalKey, SongVocal> existingByKey = new HashMap<>();
        for (SongVocal existing : existingVocals) {
            existingByKey.put(new SongVocalKey(existing.getVocal().getId()), existing);
        }

        HashSet<SongVocalKey> uniqueKeys = new HashSet<>();
        HashSet<Long> matchedIds = new HashSet<>();
        List<SongVocal> toCreate = new ArrayList<>();
        for (SongCreateRequest.SongVocalCreateRequest item : vocals) {
            if (item == null) {
                throw new IllegalArgumentException("vocals contains null item");
            }
            Long vocalId = vocalIdsByUuid.get(item.vocalResourceUuid());
            if (vocalId == null) {
                throw new IllegalArgumentException(
                        "Unknown vocalResourceUuid: " + item.vocalResourceUuid());
            }
            SongVocalKey key = new SongVocalKey(vocalId);
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate vocalResourceUuid: " + item.vocalResourceUuid());
            }

            int sortOrder = item.sortOrder() == null ? 0 : item.sortOrder();
            SongVocal existing = existingByKey.get(key);
            if (existing != null) {
                existing.updateParticipation(item.isMain(), sortOrder);
                matchedIds.add(existing.getId());
                continue;
            }

            Vocal vocal = entityManager.getReference(Vocal.class, vocalId);
            toCreate.add(SongVocal.create(song, vocal, item.isMain(), sortOrder));
        }

        List<SongVocal> toDelete =
                existingVocals.stream().filter(item -> !matchedIds.contains(item.getId())).toList();
        if (!toDelete.isEmpty()) {
            songVocalRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            songVocalRepository.saveAll(toCreate);
        }
        songVocalRepository.flush();
        return songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(song.getId());
    }

    private void syncSongRelation(Song song, UUID targetSongResourceUuid) {
        if (targetSongResourceUuid == null) {
            return;
        }

        UUID sourceSongResourceUuid = song.getResource().getUuid();
        if (sourceSongResourceUuid.equals(targetSongResourceUuid)) {
            throw new IllegalArgumentException("sourceSong and targetSong must be different");
        }

        Map<UUID, Long> songIdsByUuid = fetchSongIdsByResourceUuid(List.of(targetSongResourceUuid));
        Long targetSongId = songIdsByUuid.get(targetSongResourceUuid);
        if (targetSongId == null) {
            throw new IllegalArgumentException(
                    "Unknown targetSongResourceUuid: " + targetSongResourceUuid);
        }

        List<SongRelation> existingRelations =
                songRelationRepository.findAllBySourceSongIdOrderByIdAsc(song.getId());
        List<SongRelation> toDelete = new ArrayList<>();
        boolean matched = false;
        for (SongRelation existing : existingRelations) {
            if (!matched && existing.getTargetSong().getId().equals(targetSongId)) {
                matched = true;
                continue;
            }
            toDelete.add(existing);
        }

        if (!toDelete.isEmpty()) {
            songRelationRepository.deleteAllInBatch(toDelete);
        }
        if (!matched) {
            Song targetSong = entityManager.getReference(Song.class, targetSongId);
            songRelationRepository.save(SongRelation.create(song, targetSong));
        }
        songRelationRepository.flush();
    }

    private SongCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            SongUpdateRequest.CanonicalNameUpdateRequest canonicalName) {
        return new SongCreateRequest.CanonicalNameCreateRequest(
                canonicalName.langCode(), canonicalName.name());
    }

    private SongCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            ResourceName resourceName) {
        return new SongCreateRequest.CanonicalNameCreateRequest(
                resourceName.getLangCode(), resourceName.getName());
    }

    private List<SongCreateRequest.ResourceAliasCreateRequest> toCreateAliases(
            List<SongUpdateRequest.ResourceAliasUpdateRequest> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new SongCreateRequest.ResourceAliasCreateRequest(
                                        item.langCode(), item.name(), item.sortOrder()))
                .toList();
    }

    private List<SongCreateRequest.ResourceAliasCreateRequest> toCreateAliasesFromResourceNames(
            List<ResourceName> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new SongCreateRequest.ResourceAliasCreateRequest(
                                        item.getLangCode(), item.getName(), item.getSortOrder()))
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
                                        item.extra() == null
                                                ? null
                                                : new SongCreateRequest.SongPvExtraCreateRequest(
                                                        item.extra().audioUrl(),
                                                        item.extra().cid(),
                                                        item.extra().externalUrl()),
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

    private void saveSongRelation(Song song, UUID targetSongResourceUuid) {
        if (targetSongResourceUuid == null) {
            return;
        }

        UUID sourceSongResourceUuid = song.getResource().getUuid();
        if (sourceSongResourceUuid.equals(targetSongResourceUuid)) {
            throw new IllegalArgumentException("sourceSong and targetSong must be different");
        }

        Map<UUID, Long> songIdsByUuid = fetchSongIdsByResourceUuid(List.of(targetSongResourceUuid));
        Long targetSongId = songIdsByUuid.get(targetSongResourceUuid);
        if (targetSongId == null) {
            throw new IllegalArgumentException(
                    "Unknown targetSongResourceUuid: " + targetSongResourceUuid);
        }

        Song targetSong = entityManager.getReference(Song.class, targetSongId);
        songRelationRepository.saveAndFlush(SongRelation.create(song, targetSong));
    }

    private List<ResourceName> saveResourceNames(
            Resource resource,
            SongCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<SongCreateRequest.ResourceAliasCreateRequest> aliases) {
        if (canonicalName == null) {
            throw new IllegalArgumentException("canonicalName is required");
        }

        HashSet<String> uniqueNames = new HashSet<>();
        String normalizedCanonicalName = normalizeCanonicalName(canonicalName.name());
        uniqueNames.add(canonicalName.langCode().name() + "|" + normalizedCanonicalName);

        List<ResourceName> entities = new java.util.ArrayList<>();
        entities.add(
                ResourceName.create(
                        resource, canonicalName.langCode(), normalizedCanonicalName, true, 0));
        for (SongCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<SongCreateRequest.ResourceAliasCreateRequest>of()
                        : aliases)) {
            if (alias == null) {
                throw new IllegalArgumentException("aliases contains null item");
            }
            String normalizedAlias = normalizeCanonicalName(alias.name());
            String uniqueKey = alias.langCode().name() + "|" + normalizedAlias;
            if (!uniqueNames.add(uniqueKey)) {
                throw new IllegalArgumentException(
                        "Duplicate resource name for language and value");
            }
            entities.add(
                    ResourceName.create(
                            resource,
                            alias.langCode(),
                            normalizedAlias,
                            false,
                            alias.sortOrder() == null ? 0 : alias.sortOrder()));
        }

        return resourceNameRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(ResourceName::getSortOrder)
                                .thenComparing(ResourceName::getId))
                .toList();
    }

    private ResourceName loadCanonicalName(Resource resource) {
        List<ResourceName> existingNames =
                resourceNameRepository.findAllByResourceIdOrderBySortOrderAscIdAsc(
                        resource.getId());
        return existingNames.stream()
                .filter(ResourceName::isPrimary)
                .findFirst()
                .or(() -> existingNames.stream().findFirst())
                .orElseGet(
                        () ->
                                ResourceName.create(
                                        resource,
                                        Language.UND,
                                        resource.getCanonicalName(),
                                        true,
                                        0));
    }

    private List<ResourceName> loadAliases(Resource resource) {
        return resourceNameRepository
                .findAllByResourceIdOrderBySortOrderAscIdAsc(resource.getId())
                .stream()
                .filter(existing -> !existing.isPrimary())
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

    private List<SongLink> saveSongLinks(
            Song song, List<SongCreateRequest.SongLinkCreateRequest> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }

        List<SongLink> entities =
                links.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "links contains null item");
                                    }
                                    return SongLink.create(
                                            song,
                                            parseSongLinkType(item.type()),
                                            normalizeLinkUrl(item.url()),
                                            normalizeNullable(item.content()),
                                            item.isDeleted());
                                })
                        .toList();

        return songLinkRepository.saveAllAndFlush(entities).stream()
                .sorted(Comparator.comparing(SongLink::getId))
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
                                    SongPvProvider service = parseSongPvProvider(item.service());
                                    SongPvExtraValues extra =
                                            normalizeSongPvExtra(service, item.extra());
                                    return SongPv.create(
                                            song,
                                            service,
                                            normalizeRequired(item.videoKey(), "videoKey"),
                                            normalizeNullable(item.title()),
                                            normalizeNullable(item.thumbnailUrl()),
                                            normalizeNullable(item.uploaderKey()),
                                            item.durationSeconds(),
                                            item.isOfficial(),
                                            item.publishedAt(),
                                            extra.audioUrl(),
                                            extra.cid(),
                                            extra.externalUrl(),
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

    private SongPvExtraValues normalizeSongPvExtra(
            SongPvProvider service, SongCreateRequest.SongPvExtraCreateRequest extra) {
        if (extra == null || service == null) {
            return SongPvExtraValues.empty();
        }

        String audioUrl = normalizeNullable(extra.audioUrl());
        Long cid = extra.cid();
        if (cid != null && cid < 0) {
            throw new IllegalArgumentException("pvs.extra.cid must be >= 0");
        }
        String externalUrl = normalizeNullable(extra.externalUrl());

        return switch (service) {
            case PIAPRO -> new SongPvExtraValues(audioUrl, null, null);
            case BILIBILI -> new SongPvExtraValues(null, cid, null);
            case BANDCAMP -> new SongPvExtraValues(null, null, externalUrl);
            default -> SongPvExtraValues.empty();
        };
    }

    private SongLinkType parseSongLinkType(String value) {
        return parseEnum(value, SongLinkType.class, "links.type");
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

    private String normalizeLinkUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("links.url is required");
        }
        return url.trim();
    }

    private Set<SongArtistRole> parseSongArtistRoles(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("artists.roles is required");
        }
        return new LinkedHashSet<>(
                roles.stream()
                        .map(role -> parseEnum(role, SongArtistRole.class, "artists.roles"))
                        .sorted(Comparator.comparing(Enum::name))
                        .toList());
    }

    private String lyricLangCodesKey(Set<Language> langCodes) {
        if (langCodes == null || langCodes.isEmpty()) {
            throw new IllegalArgumentException("lyrics.langCodes is required");
        }
        return String.join("|", langCodes.stream().map(Enum::name).sorted().toList());
    }

    private String songArtistRolesKey(Set<SongArtistRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("artists.roles is required");
        }
        return String.join("|", roles.stream().map(Enum::name).sorted().toList());
    }

    private record ResourceNameKey(Language langCode, String name) {}

    private record DesiredResourceName(
            Language langCode, String name, boolean isPrimary, int sortOrder) {}

    private record AclKey(
            AclAction action, AclSubjectType subjectType, String subjectValue, int priority) {}

    private record SongLinkKey(SongLinkType songLinkType, String url) {}

    private record SongLyricKey(String langCodesKey, boolean isPrimary, int sortOrder) {}

    private record SongPvKey(SongPvProvider service, String videoKey) {}

    private record SongPvExtraValues(String audioUrl, Long cid, String externalUrl) {
        private static SongPvExtraValues empty() {
            return new SongPvExtraValues(null, null, null);
        }
    }

    private record SongArtistKey(Long artistId, String rolesKey) {}

    private record SongVocalKey(Long vocalId) {}

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

    private Map<Long, String> loadLocalizedNamesByResourceId(List<Song> songs) {
        Language language = resolveCurrentLanguage();
        if (language == null || songs.isEmpty()) {
            return Map.of();
        }

        List<Long> resourceIds =
                songs.stream().map(song -> song.getResource().getId()).distinct().toList();

        Map<Long, String> localizedNamesByResourceId = new HashMap<>();
        for (ResourceName resourceName :
                resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        resourceIds)) {
            if (resourceName.getLangCode() != language) {
                continue;
            }
            localizedNamesByResourceId.putIfAbsent(
                    resourceName.getResource().getId(), resourceName.getName());
        }
        return localizedNamesByResourceId;
    }

    private Language resolveCurrentLanguage() {
        return switch (LocaleContextHolder.getLocale().getLanguage()) {
            case "ko" -> Language.KO;
            case "en" -> Language.EN;
            case "ja" -> Language.JA;
            case "zh" -> Language.ZH;
            default -> null;
        };
    }

    private SongElementResponse toSummary(Song song, Map<Long, String> localizedNamesByResourceId) {
        Resource resource = song.getResource();
        return new SongElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                localizedNamesByResourceId.get(resource.getId()),
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
        snapshot.set("links", buildSongLinksSnapshot(song));
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

    private ArrayNode buildSongLinksSnapshot(Song song) {
        ArrayNode links = objectMapper.createArrayNode();
        for (SongLink item : songLinkRepository.findAllBySongIdOrderByIdAsc(song.getId())) {
            ObjectNode link = objectMapper.createObjectNode();
            link.put("type", item.getSongLinkType().name());
            link.put("url", item.getUrl());
            if (item.getContent() == null) {
                link.putNull("content");
            } else {
                link.put("content", item.getContent());
            }
            link.put("isDeleted", item.isDeleted());
            links.add(link);
        }
        return links;
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
            ObjectNode extra = buildSongPvExtraSnapshot(item);
            if (extra == null) {
                pv.putNull("extra");
            } else {
                pv.set("extra", extra);
            }
            pv.put("sortOrder", item.getSortOrder());
            pvs.add(pv);
        }
        return pvs;
    }

    private ObjectNode buildSongPvExtraSnapshot(SongPv item) {
        String audioUrl = normalizeNullable(item.getPiaproAudioUrl());
        Long cid = item.getBilibiliCid();
        String externalUrl = normalizeNullable(item.getBandcampExternalUrl());
        if (audioUrl == null && cid == null && externalUrl == null) {
            return null;
        }

        ObjectNode extra = objectMapper.createObjectNode();
        if (audioUrl == null) {
            extra.putNull("audioUrl");
        } else {
            extra.put("audioUrl", audioUrl);
        }
        if (cid == null) {
            extra.putNull("cid");
        } else {
            extra.put("cid", cid);
        }
        if (externalUrl == null) {
            extra.putNull("externalUrl");
        } else {
            extra.put("externalUrl", externalUrl);
        }
        return extra;
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
