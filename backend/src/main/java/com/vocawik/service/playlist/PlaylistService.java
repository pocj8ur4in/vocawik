package com.vocawik.service.playlist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.acl.Acl;
import com.vocawik.domain.acl.AclAction;
import com.vocawik.domain.acl.AclEffect;
import com.vocawik.domain.acl.AclSubjectType;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.playlist.PlaylistSong;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.playlist.PlaylistCreateRequest;
import com.vocawik.dto.playlist.PlaylistElementResponse;
import com.vocawik.dto.playlist.PlaylistListResponse;
import com.vocawik.dto.playlist.PlaylistPlaybackListResponse;
import com.vocawik.dto.playlist.PlaylistSongElementResponse;
import com.vocawik.dto.playlist.PlaylistSongListResponse;
import com.vocawik.dto.playlist.PlaylistSuggestionElementResponse;
import com.vocawik.dto.playlist.PlaylistSuggestionListResponse;
import com.vocawik.dto.playlist.PlaylistUpdateRequest;
import com.vocawik.dto.song.SongPlaybackElementResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.playlist.PlaylistCriteria;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.security.SecurityRoleUtils;
import com.vocawik.service.acl.AclPermissionService;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.service.song.SongService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for playlist queries and writes. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed dependencies are stored for internal orchestration only.")
public class PlaylistService {

    private static final int PLAYLIST_SUGGESTION_LIMIT = 10;
    private static final int DEFAULT_SONG_PAGE_SIZE = 50;
    private static final int MAX_SONG_PAGE_SIZE = 100;

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final SongRepository songRepository;
    private final AclPermissionService aclPermissionService;
    private final ResourceHistoryService resourceHistoryService;
    private final SongService songService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlaylistListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        boolean includeDeleted = aclPermissionService.isCurrentAdmin();
        ResourceStatus effectiveStatus = includeDeleted ? status : ResourceStatus.ACTIVE;
        Page<Playlist> result =
                playlistRepository.search(
                        new PlaylistCriteria(effectiveStatus, normalizedQuery, includeDeleted),
                        pageable);

        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceId(result.getContent());
        List<PlaylistElementResponse> items =
                result.getContent().stream()
                        .map(playlist -> toSummary(playlist, localizedNamesByResourceId))
                        .toList();

        return new PlaylistListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public PlaylistSuggestionListResponse suggest(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new PlaylistSuggestionListResponse(List.of());
        }

        LinkedHashMap<String, LinkedHashMap<Long, UUID>> resourceRefsByName = new LinkedHashMap<>();
        resourceNameRepository
                .findPlaylistSuggestionCandidates(
                        ResourceStatus.ACTIVE,
                        normalizedQuery,
                        org.springframework.data.domain.PageRequest.of(
                                0, PLAYLIST_SUGGESTION_LIMIT * 3))
                .forEach(
                        resourceName -> {
                            Resource resource = resourceName.getResource();
                            resourceRefsByName
                                    .computeIfAbsent(
                                            resourceName.getName(),
                                            ignored -> new LinkedHashMap<>())
                                    .putIfAbsent(resource.getId(), resource.getUuid());
                        });

        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceIds(
                        resourceRefsByName.values().stream()
                                .flatMap(resourceRefs -> resourceRefs.keySet().stream())
                                .distinct()
                                .toList());

        return new PlaylistSuggestionListResponse(
                resourceRefsByName.entrySet().stream()
                        .limit(PLAYLIST_SUGGESTION_LIMIT)
                        .map(
                                entry -> {
                                    LinkedHashMap<Long, UUID> resourceRefs = entry.getValue();
                                    boolean hasMultipleResources = resourceRefs.size() > 1;
                                    UUID resourceUuid =
                                            hasMultipleResources
                                                    ? null
                                                    : resourceRefs.values().iterator().next();
                                    String localizedName =
                                            hasMultipleResources
                                                    ? null
                                                    : localizedNamesByResourceId.get(
                                                            resourceRefs
                                                                    .keySet()
                                                                    .iterator()
                                                                    .next());
                                    return new PlaylistSuggestionElementResponse(
                                            resourceUuid,
                                            entry.getKey(),
                                            localizedName,
                                            hasMultipleResources);
                                })
                        .toList());
    }

    @Transactional(readOnly = true)
    public PlaylistPlaybackListResponse getPlayback(
            UUID resourceUuid, String preferredPvService, String cursor, Integer limit) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = playlist.getResource();
        assertVisiblePlaylistResource(resource);

        PlaylistSongCursor decodedCursor = decodeCursor(cursor);
        int pageSize = sanitizeSongPageSize(limit);
        PlaylistSongSlice playlistSongSlice =
                loadVisiblePlaylistSongs(
                        playlist.getId(),
                        decodedCursor == null ? null : decodedCursor.sortOrder(),
                        decodedCursor == null ? null : decodedCursor.id(),
                        pageSize);
        boolean hasNext = playlistSongSlice.hasNext();
        List<PlaylistSong> playlistSongs = playlistSongSlice.items();
        List<SongPlaybackElementResponse> playbackItems =
                songService.buildPlaybackItems(
                        playlistSongs.stream().map(PlaylistSong::getSong).toList(),
                        preferredPvService);
        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceIds(List.of(resource.getId()));

        return new PlaylistPlaybackListResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                localizedNamesByResourceId.get(resource.getId()),
                resource.getThumbnailUrl(),
                toPlaybackSongs(playlistSongs, playbackItems),
                hasNext && !playlistSongs.isEmpty() ? encodeCursor(playlistSongs.getLast()) : null,
                hasNext);
    }

    @Transactional(readOnly = true)
    public PlaylistSongListResponse getSongs(UUID resourceUuid, String cursor, Integer limit) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Resource resource = playlist.getResource();
        assertVisiblePlaylistResource(resource);

        PlaylistSongCursor decodedCursor = decodeCursor(cursor);
        int pageSize = sanitizeSongPageSize(limit);
        PlaylistSongSlice playlistSongSlice =
                loadVisiblePlaylistSongs(
                        playlist.getId(),
                        decodedCursor == null ? null : decodedCursor.sortOrder(),
                        decodedCursor == null ? null : decodedCursor.id(),
                        pageSize);
        boolean hasNext = playlistSongSlice.hasNext();
        List<PlaylistSong> visibleSongs = playlistSongSlice.items();
        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceIds(
                        visibleSongs.stream()
                                .map(playlistSong -> playlistSong.getSong().getResource().getId())
                                .distinct()
                                .toList());

        return new PlaylistSongListResponse(
                visibleSongs.stream()
                        .map(song -> toPlaylistSongElement(song, localizedNamesByResourceId))
                        .toList(),
                hasNext ? encodeCursor(visibleSongs.getLast()) : null,
                hasNext);
    }

    @Transactional
    public UUID create(PlaylistCreateRequest request) {
        PlaylistCreateRequest.CanonicalNameCreateRequest canonicalName = request.canonicalName();
        Playlist playlist =
                Playlist.create(
                        normalizeCanonicalName(canonicalName.name()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()),
                        request.isPublic() == null || request.isPublic());

        Resource resource = resourceRepository.save(playlist.getResource());
        playlistRepository.save(playlist);
        applyAdminResourceState(resource, request.status(), request.isDeleted());

        saveResourceNames(resource, canonicalName, request.aliases());
        if (SecurityRoleUtils.isAdmin()) {
            saveAcls(resource, request.acls());
        }
        savePlaylistSongs(playlist, request.songs());

        resourceHistoryService.recordCreate(resource, buildHistorySnapshot(playlist, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    @Transactional
    public UUID update(UUID resourceUuid, PlaylistUpdateRequest request) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuid(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertMutable(playlist);

        Resource resource = playlist.getResource();
        aclPermissionService.assertCanEdit(resource);
        updatePlaylistFields(playlist, resource, request);
        applyAdminResourceState(resource, request.status(), request.isDeleted());

        syncResourceNames(
                resource,
                toCreateCanonical(request.canonicalName()),
                toCreateAliases(request.aliases()));
        if (SecurityRoleUtils.isAdmin()) {
            syncAcls(resource, toCreateAcls(request.acls()));
        }
        syncPlaylistSongs(playlist, toCreateSongs(request.songs()));

        resourceHistoryService.recordUpdate(resource, buildHistorySnapshot(playlist, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    @Transactional
    public void delete(UUID resourceUuid) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        assertMutable(playlist);

        Resource resource = playlist.getResource();
        aclPermissionService.assertCanDelete(resource);
        JsonNode snapshot = buildHistorySnapshot(playlist, resource);

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

    private void assertMutable(Playlist playlist) {
        if (playlist.isSystemManaged()) {
            throw new BusinessException(ErrorCode.PLAYLIST_SYSTEM_MANAGED);
        }
    }

    private String normalizeCanonicalName(String canonicalName) {
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName is required");
        }
        return canonicalName.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void applyAdminResourceState(
            Resource resource, ResourceStatus status, Boolean isDeleted) {
        if (!SecurityRoleUtils.isAdmin()) {
            return;
        }
        if (status != null) {
            resource.updateStatus(status);
        }
        if (isDeleted != null) {
            resource.updateDeleted(isDeleted);
        }
    }

    private Map<Long, String> loadLocalizedNamesByResourceId(List<Playlist> playlists) {
        Language language = resolveCurrentLanguage();
        if (language == null || playlists.isEmpty()) {
            return Map.of();
        }

        return loadLocalizedNamesByResourceIds(
                playlists.stream()
                        .map(playlist -> playlist.getResource().getId())
                        .distinct()
                        .toList());
    }

    private Map<Long, String> loadLocalizedNamesByResourceIds(List<Long> resourceIds) {
        Language language = resolveCurrentLanguage();
        if (language == null || resourceIds.isEmpty()) {
            return Map.of();
        }

        List<ResourceName> localizedNames =
                resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        resourceIds);
        if (localizedNames == null) {
            return Map.of();
        }

        Map<Long, String> localizedNamesByResourceId = new HashMap<>();
        for (ResourceName resourceName : localizedNames) {
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

    private PlaylistElementResponse toSummary(
            Playlist playlist, Map<Long, String> localizedNamesByResourceId) {
        Resource resource = playlist.getResource();
        return new PlaylistElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                localizedNamesByResourceId.get(resource.getId()),
                resource.getStatus().name(),
                resource.isDeleted(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    private PlaylistSongElementResponse toPlaylistSongElement(
            PlaylistSong playlistSong, Map<Long, String> localizedNamesByResourceId) {
        com.vocawik.domain.song.Song song = playlistSong.getSong();
        Resource resource = song.getResource();
        return new PlaylistSongElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                localizedNamesByResourceId.get(resource.getId()),
                resource.getThumbnailUrl(),
                song.getSongType().name(),
                playlistSong.getSortOrder());
    }

    private PlaylistSongSlice loadVisiblePlaylistSongs(
            Long playlistId, Integer cursorSortOrder, Long cursorId, int pageSize) {
        ArrayList<PlaylistSong> visibleSongs = new ArrayList<>(pageSize + 1);
        Integer nextSortOrder = cursorSortOrder;
        Long nextCursorId = cursorId;
        int fetchSize = Math.max(pageSize + 1, 100);

        while (visibleSongs.size() < pageSize + 1) {
            List<PlaylistSong> batch =
                    playlistSongRepository.findPageWithSongResourceByPlaylistIdAfterCursor(
                            playlistId, nextSortOrder, nextCursorId, PageRequest.of(0, fetchSize));
            if (batch.isEmpty()) {
                break;
            }

            batch.stream()
                    .filter(
                            playlistSong ->
                                    isVisibleLinkedSong(playlistSong.getSong().getResource()))
                    .forEach(visibleSongs::add);

            PlaylistSong last = batch.getLast();
            nextSortOrder = last.getSortOrder();
            nextCursorId = last.getId();

            if (batch.size() < fetchSize) {
                break;
            }
        }

        boolean hasNext = visibleSongs.size() > pageSize;
        List<PlaylistSong> items =
                hasNext
                        ? List.copyOf(visibleSongs.subList(0, pageSize))
                        : List.copyOf(visibleSongs);
        return new PlaylistSongSlice(items, hasNext);
    }

    private void assertVisiblePlaylistResource(Resource resource) {
        if (!aclPermissionService.isCurrentAdmin()
                && (resource.isDeleted() || resource.getStatus() != ResourceStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        aclPermissionService.assertCanRead(resource);
    }

    private boolean isVisibleLinkedSong(Resource resource) {
        if (resource == null) {
            return false;
        }
        if (aclPermissionService.isCurrentAdmin()) {
            return aclPermissionService.isAllowed(resource, AclAction.READ);
        }
        return !resource.isDeleted()
                && ResourceStatus.ACTIVE.equals(resource.getStatus())
                && aclPermissionService.isAllowed(resource, AclAction.READ);
    }

    private int sanitizeSongPageSize(Integer limit) {
        if (limit == null) {
            return DEFAULT_SONG_PAGE_SIZE;
        }
        return Math.max(1, Math.min(limit, MAX_SONG_PAGE_SIZE));
    }

    private String encodeCursor(PlaylistSong playlistSong) {
        String raw = playlistSong.getSortOrder() + ":" + playlistSong.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private PlaylistSongCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("playlist songs cursor is invalid");
            }
            return new PlaylistSongCursor(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("playlist songs cursor is invalid");
        }
    }

    private List<PlaylistPlaybackListResponse.PlaylistPlaybackSong> toPlaybackSongs(
            List<PlaylistSong> playlistSongs, List<SongPlaybackElementResponse> playbackItems) {
        if (playlistSongs.size() != playbackItems.size()) {
            throw new IllegalStateException("playlist songs and playback items size mismatch");
        }

        List<PlaylistPlaybackListResponse.PlaylistPlaybackSong> items =
                new ArrayList<>(playlistSongs.size());
        for (int index = 0; index < playlistSongs.size(); index++) {
            PlaylistSong playlistSong = playlistSongs.get(index);
            SongPlaybackElementResponse playbackItem = playbackItems.get(index);
            items.add(
                    new PlaylistPlaybackListResponse.PlaylistPlaybackSong(
                            playbackItem.resourceUuid(),
                            playbackItem.canonicalName(),
                            playbackItem.localizedName(),
                            playbackItem.thumbnailUrl(),
                            playbackItem.subtitle(),
                            playlistSong.getSortOrder(),
                            playbackItem.pvs()));
        }
        return items;
    }

    private List<ResourceName> saveResourceNames(
            Resource resource,
            PlaylistCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<PlaylistCreateRequest.ResourceAliasCreateRequest> aliases) {
        if (canonicalName == null) {
            throw new IllegalArgumentException("canonicalName is required");
        }

        HashSet<String> uniqueNames = new HashSet<>();
        String normalizedCanonicalName = normalizeCanonicalName(canonicalName.name());
        uniqueNames.add(canonicalName.langCode().name() + "|" + normalizedCanonicalName);

        List<ResourceName> entities = new ArrayList<>();
        entities.add(
                ResourceName.create(
                        resource, canonicalName.langCode(), normalizedCanonicalName, true, 0));
        for (PlaylistCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<PlaylistCreateRequest.ResourceAliasCreateRequest>of()
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

    private List<Acl> saveAcls(
            Resource resource, List<PlaylistCreateRequest.ResourceAclCreateRequest> acls) {
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

    private List<PlaylistSong> savePlaylistSongs(
            Playlist playlist, List<PlaylistCreateRequest.PlaylistSongCreateRequest> songs) {
        if (songs == null || songs.isEmpty()) {
            return List.of();
        }

        HashSet<UUID> uniqueSongUuids = new HashSet<>();
        HashSet<Integer> uniqueSortOrders = new HashSet<>();
        for (PlaylistCreateRequest.PlaylistSongCreateRequest item : songs) {
            if (item == null) {
                throw new IllegalArgumentException("songs contains null item");
            }
            if (!uniqueSongUuids.add(item.songResourceUuid())) {
                throw new IllegalArgumentException(
                        "Duplicate songResourceUuid: " + item.songResourceUuid());
            }
            if (!uniqueSortOrders.add(item.sortOrder())) {
                throw new IllegalArgumentException("Duplicate sortOrder in songs");
            }
        }

        List<UUID> songUuids =
                songs.stream()
                        .map(PlaylistCreateRequest.PlaylistSongCreateRequest::songResourceUuid)
                        .toList();
        Map<UUID, Long> songIdsByUuid =
                songRepository.findResourceRefsByResourceUuids(songUuids).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ResourceRefProjection::getResourceUuid,
                                        ResourceRefProjection::getId));

        if (songIdsByUuid.size() != songUuids.size()) {
            for (UUID songUuid : songUuids) {
                if (!songIdsByUuid.containsKey(songUuid)) {
                    throw new IllegalArgumentException("Unknown songResourceUuid: " + songUuid);
                }
            }
        }

        List<PlaylistSong> entities =
                songs.stream()
                        .map(
                                item ->
                                        PlaylistSong.create(
                                                playlist,
                                                entityManager.getReference(
                                                        com.vocawik.domain.song.Song.class,
                                                        songIdsByUuid.get(item.songResourceUuid())),
                                                item.sortOrder()))
                        .toList();

        return playlistSongRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(PlaylistSong::getSortOrder)
                                .thenComparing(PlaylistSong::getId))
                .toList();
    }

    private void updatePlaylistFields(
            Playlist playlist, Resource resource, PlaylistUpdateRequest request) {
        resource.updateCanonicalName(normalizeCanonicalName(request.canonicalName().name()));
        resource.updateThumbnailUrl(normalizeNullable(request.thumbnailUrl()));
        playlist.update(normalizeNullable(request.content()), request.isPublic());
    }

    private PlaylistCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            PlaylistUpdateRequest.CanonicalNameUpdateRequest canonicalName) {
        return new PlaylistCreateRequest.CanonicalNameCreateRequest(
                canonicalName.langCode(), canonicalName.name());
    }

    private List<PlaylistCreateRequest.ResourceAliasCreateRequest> toCreateAliases(
            List<PlaylistUpdateRequest.ResourceAliasUpdateRequest> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new PlaylistCreateRequest.ResourceAliasCreateRequest(
                                        item.langCode(), item.name(), item.sortOrder()))
                .toList();
    }

    private List<PlaylistCreateRequest.ResourceAliasCreateRequest> toCreateAliasesFromResourceNames(
            List<ResourceName> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new PlaylistCreateRequest.ResourceAliasCreateRequest(
                                        item.getLangCode(), item.getName(), item.getSortOrder()))
                .toList();
    }

    private List<PlaylistCreateRequest.ResourceAclCreateRequest> toCreateAcls(
            List<PlaylistUpdateRequest.ResourceAclUpdateRequest> acls) {
        return acls.stream()
                .map(
                        item ->
                                new PlaylistCreateRequest.ResourceAclCreateRequest(
                                        item.action(),
                                        item.subjectType(),
                                        item.subjectValue(),
                                        item.effect(),
                                        item.priority(),
                                        item.expiresAt()))
                .toList();
    }

    private List<PlaylistCreateRequest.PlaylistSongCreateRequest> toCreateSongs(
            List<PlaylistUpdateRequest.PlaylistSongUpdateRequest> songs) {
        return songs.stream()
                .map(
                        item ->
                                new PlaylistCreateRequest.PlaylistSongCreateRequest(
                                        item.songResourceUuid(), item.sortOrder()))
                .toList();
    }

    private void syncResourceNames(
            Resource resource,
            PlaylistCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<PlaylistCreateRequest.ResourceAliasCreateRequest> aliases) {
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

        for (PlaylistCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<PlaylistCreateRequest.ResourceAliasCreateRequest>of()
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

    private void syncAcls(
            Resource resource, List<PlaylistCreateRequest.ResourceAclCreateRequest> acls) {
        List<Acl> existingAcls =
                aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resource.getId());
        if (acls.isEmpty()) {
            if (!existingAcls.isEmpty()) {
                aclRepository.deleteAllInBatch(existingAcls);
                aclRepository.flush();
            }
            return;
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
        for (PlaylistCreateRequest.ResourceAclCreateRequest item : acls) {
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
    }

    private void syncPlaylistSongs(
            Playlist playlist, List<PlaylistCreateRequest.PlaylistSongCreateRequest> songs) {
        List<PlaylistSong> existingSongs =
                playlistSongRepository.findAllByPlaylistIdOrderBySortOrderAscIdAsc(
                        playlist.getId());
        if (songs.isEmpty()) {
            if (!existingSongs.isEmpty()) {
                playlistSongRepository.deleteAllInBatch(existingSongs);
                playlistSongRepository.flush();
            }
            return;
        }

        HashSet<UUID> uniqueSongUuids = new HashSet<>();
        HashSet<Integer> uniqueSortOrders = new HashSet<>();
        for (PlaylistCreateRequest.PlaylistSongCreateRequest item : songs) {
            if (item == null) {
                throw new IllegalArgumentException("songs contains null item");
            }
            if (!uniqueSongUuids.add(item.songResourceUuid())) {
                throw new IllegalArgumentException(
                        "Duplicate songResourceUuid: " + item.songResourceUuid());
            }
            if (!uniqueSortOrders.add(item.sortOrder())) {
                throw new IllegalArgumentException("Duplicate sortOrder in songs");
            }
        }

        List<UUID> songUuids =
                songs.stream()
                        .map(PlaylistCreateRequest.PlaylistSongCreateRequest::songResourceUuid)
                        .toList();
        Map<UUID, Long> songIdsByUuid =
                songRepository.findResourceRefsByResourceUuids(songUuids).stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        ResourceRefProjection::getResourceUuid,
                                        ResourceRefProjection::getId));
        for (UUID songUuid : songUuids) {
            if (!songIdsByUuid.containsKey(songUuid)) {
                throw new IllegalArgumentException("Unknown songResourceUuid: " + songUuid);
            }
        }

        Map<Long, PlaylistSong> existingBySongId = new HashMap<>();
        for (PlaylistSong existing : existingSongs) {
            existingBySongId.put(existing.getSong().getId(), existing);
        }

        List<PlaylistSongSortUpdate> toUpdateSortOrders = new ArrayList<>();
        List<PlaylistSong> toCreate = new ArrayList<>();
        for (PlaylistCreateRequest.PlaylistSongCreateRequest item : songs) {
            Long songId = songIdsByUuid.get(item.songResourceUuid());
            PlaylistSong existing = existingBySongId.remove(songId);
            if (existing == null) {
                toCreate.add(
                        PlaylistSong.create(
                                playlist,
                                entityManager.getReference(
                                        com.vocawik.domain.song.Song.class, songId),
                                item.sortOrder()));
                continue;
            }
            if (existing.getSortOrder() != item.sortOrder()) {
                toUpdateSortOrders.add(new PlaylistSongSortUpdate(existing, item.sortOrder()));
            }
        }

        if (!existingBySongId.isEmpty()) {
            playlistSongRepository.deleteAllInBatch(new ArrayList<>(existingBySongId.values()));
            playlistSongRepository.flush();
        }

        if (!toUpdateSortOrders.isEmpty()) {
            int maxSortOrder = 0;
            for (PlaylistSong existing : existingSongs) {
                if (existing.getSortOrder() > maxSortOrder) {
                    maxSortOrder = existing.getSortOrder();
                }
            }
            for (PlaylistCreateRequest.PlaylistSongCreateRequest item : songs) {
                if (item.sortOrder() > maxSortOrder) {
                    maxSortOrder = item.sortOrder();
                }
            }

            int temporarySortOrder = maxSortOrder + 1;
            for (PlaylistSongSortUpdate update : toUpdateSortOrders) {
                update.playlistSong().updateSortOrder(temporarySortOrder++);
            }
            playlistSongRepository.flush();

            for (PlaylistSongSortUpdate update : toUpdateSortOrders) {
                update.playlistSong().updateSortOrder(update.targetSortOrder());
            }
        }

        if (!toCreate.isEmpty()) {
            playlistSongRepository.saveAll(toCreate);
        }
        playlistSongRepository.flush();
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

    private record ResourceNameKey(Language langCode, String name) {}

    private record DesiredResourceName(
            Language langCode, String name, boolean isPrimary, int sortOrder) {}

    private record AclKey(
            AclAction action, AclSubjectType subjectType, String subjectValue, int priority) {}

    private record PlaylistSongSortUpdate(PlaylistSong playlistSong, int targetSortOrder) {}

    private record PlaylistSongSlice(List<PlaylistSong> items, boolean hasNext) {}

    private JsonNode buildHistorySnapshot(Playlist playlist, Resource resource) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("canonicalName", resource.getCanonicalName());
        putNullableText(data, "thumbnailUrl", resource.getThumbnailUrl());
        putNullableText(data, "content", playlist.getContent());
        data.put("isPublic", playlist.isPublic());
        data.set("names", buildNamesSnapshot(resource.getId()));
        data.set("acls", buildAclsSnapshot(resource.getId()));
        data.set("songs", buildSongsSnapshot(playlist.getId()));
        return data;
    }

    private ArrayNode buildNamesSnapshot(Long resourceId) {
        ArrayNode array = objectMapper.createArrayNode();
        for (ResourceName name :
                resourceNameRepository.findAllByResourceIdOrderBySortOrderAscIdAsc(resourceId)) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("langCode", name.getLangCode().name());
            item.put("name", name.getName());
            item.put("isPrimary", name.isPrimary());
            item.put("sortOrder", name.getSortOrder());
            array.add(item);
        }
        return array;
    }

    private ArrayNode buildAclsSnapshot(Long resourceId) {
        ArrayNode array = objectMapper.createArrayNode();
        for (Acl acl : aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resourceId)) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("action", acl.getAction().name());
            item.put("subjectType", acl.getSubjectType().name());
            item.put("subjectValue", acl.getSubjectValue());
            item.put("effect", acl.getEffect().name());
            item.put("priority", acl.getPriority());
            if (acl.getExpiresAt() == null) {
                item.putNull("expiresAt");
            } else {
                item.put("expiresAt", acl.getExpiresAt().toString());
            }
            array.add(item);
        }
        return array;
    }

    private ArrayNode buildSongsSnapshot(Long playlistId) {
        ArrayNode array = objectMapper.createArrayNode();
        for (PlaylistSong playlistSong :
                playlistSongRepository.findAllByPlaylistIdOrderBySortOrderAscIdAsc(playlistId)) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("songResourceUuid", playlistSong.getSong().getResource().getUuid().toString());
            item.put("sortOrder", playlistSong.getSortOrder());
            array.add(item);
        }
        return array;
    }

    private void putNullableText(ObjectNode objectNode, String fieldName, String value) {
        if (value == null) {
            objectNode.putNull(fieldName);
            return;
        }
        objectNode.put(fieldName, value);
    }

    private record PlaylistSongCursor(int sortOrder, long id) {}
}
