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
import com.vocawik.dto.playlist.PlaylistUpdateRequest;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.playlist.PlaylistCriteria;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final SongRepository songRepository;
    private final ResourceHistoryService resourceHistoryService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PlaylistListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Page<Playlist> result =
                playlistRepository.search(new PlaylistCriteria(status, normalizedQuery), pageable);

        List<PlaylistElementResponse> items =
                result.getContent().stream().map(this::toSummary).toList();

        return new PlaylistListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
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

        saveResourceNames(resource, canonicalName, request.aliases());
        saveAcls(resource, request.acls());
        savePlaylistSongs(playlist, request.songs());

        resourceHistoryService.recordCreate(resource, buildHistorySnapshot(playlist, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    @Transactional
    public UUID update(UUID resourceUuid, PlaylistUpdateRequest request) {
        Playlist playlist =
                playlistRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = playlist.getResource();
        updatePlaylistFields(playlist, resource, request);

        if (request.canonicalName() != null || request.aliases() != null) {
            PlaylistCreateRequest.CanonicalNameCreateRequest canonicalName =
                    request.canonicalName() == null
                            ? toCreateCanonical(loadCanonicalName(resource))
                            : toCreateCanonical(request.canonicalName());
            List<PlaylistCreateRequest.ResourceAliasCreateRequest> aliases =
                    request.aliases() == null
                            ? toCreateAliasesFromResourceNames(loadAliases(resource))
                            : toCreateAliases(request.aliases());
            replaceResourceNames(resource, canonicalName, aliases);
        }
        if (request.acls() != null) {
            replaceAcls(resource, toCreateAcls(request.acls()));
        }
        if (request.songs() != null) {
            replacePlaylistSongs(playlist, toCreateSongs(request.songs()));
        }

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

        Resource resource = playlist.getResource();
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

    private PlaylistElementResponse toSummary(Playlist playlist) {
        Resource resource = playlist.getResource();
        return new PlaylistElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
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
        if (request.canonicalName() != null) {
            resource.updateCanonicalName(normalizeCanonicalName(request.canonicalName().name()));
        }
        if (request.thumbnailUrl() != null) {
            resource.updateThumbnailUrl(normalizeNullable(request.thumbnailUrl()));
        }
        playlist.update(
                request.content() != null
                        ? normalizeNullable(request.content())
                        : playlist.getContent(),
                request.isPublic());
    }

    private PlaylistCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            PlaylistUpdateRequest.CanonicalNameUpdateRequest canonicalName) {
        return new PlaylistCreateRequest.CanonicalNameCreateRequest(
                canonicalName.langCode(), canonicalName.name());
    }

    private PlaylistCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            ResourceName resourceName) {
        return new PlaylistCreateRequest.CanonicalNameCreateRequest(
                resourceName.getLangCode(), resourceName.getName());
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

    private void replaceResourceNames(
            Resource resource,
            PlaylistCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<PlaylistCreateRequest.ResourceAliasCreateRequest> aliases) {
        resourceNameRepository.deleteByResourceId(resource.getId());
        saveResourceNames(resource, canonicalName, aliases);
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

    private void replaceAcls(
            Resource resource, List<PlaylistCreateRequest.ResourceAclCreateRequest> acls) {
        aclRepository.deleteByResourceId(resource.getId());
        saveAcls(resource, acls);
    }

    private void replacePlaylistSongs(
            Playlist playlist, List<PlaylistCreateRequest.PlaylistSongCreateRequest> songs) {
        playlistSongRepository.deleteByPlaylistId(playlist.getId());
        savePlaylistSongs(playlist, songs);
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
}
