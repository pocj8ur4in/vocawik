package com.vocawik.service.artist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vocawik.domain.acl.Acl;
import com.vocawik.domain.acl.AclAction;
import com.vocawik.domain.acl.AclEffect;
import com.vocawik.domain.acl.AclSubjectType;
import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.artist.ArtistGroup;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.artist.ArtistCreateRequest;
import com.vocawik.dto.artist.ArtistElementResponse;
import com.vocawik.dto.artist.ArtistListResponse;
import com.vocawik.dto.artist.ArtistUpdateRequest;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistCriteria;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching artists. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ObjectMapper is a Spring-managed infrastructure bean and is not exposed externally.")
public class ArtistService {
    private final ArtistRepository artistRepository;
    private final ArtistGroupRepository artistGroupRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final ResourceHistoryService resourceHistoryService;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * Creates an artist and initializes resource projection payload.
     *
     * @param request create payload
     * @return created artist resource UUID
     */
    @Transactional
    public UUID create(ArtistCreateRequest request) {
        JsonNode links = toJsonNode(request.links());
        validateLinks(links);

        Artist artist =
                Artist.create(
                        normalizeCanonicalName(request.canonicalName()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()),
                        links);

        Resource resource = resourceRepository.save(artist.getResource());
        artistRepository.saveAndFlush(artist);

        saveResourceNames(resource, request.names());
        saveAcls(resource, request.acls());
        saveArtistMemberships(artist, request.members());

        resourceHistoryService.recordCreate(resource, buildHistorySnapshot(artist, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Updates an artist and optionally replaces child collections.
     *
     * @param resourceUuid artist resource UUID
     * @param request update payload
     * @return updated artist resource UUID
     */
    @Transactional
    public UUID update(UUID resourceUuid, ArtistUpdateRequest request) {
        Artist artist =
                artistRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = artist.getResource();
        updateArtistFields(artist, resource, request);

        if (request.names() != null) {
            replaceResourceNames(resource, toCreateNames(request.names()));
        }
        if (request.acls() != null) {
            replaceAcls(resource, toCreateAcls(request.acls()));
        }
        if (request.members() != null) {
            replaceArtistMemberships(artist, toCreateMembers(request.members()));
        }

        resourceHistoryService.recordUpdate(resource, buildHistorySnapshot(artist, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Soft-deletes an artist and records delete history.
     *
     * @param resourceUuid artist resource UUID
     */
    @Transactional
    public void delete(UUID resourceUuid) {
        Artist artist =
                artistRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = artist.getResource();
        JsonNode snapshot = buildHistorySnapshot(artist, resource);

        resource.softDelete();
        resourceHistoryService.recordDelete(resource, snapshot);
        resourceRepository.saveAndFlush(resource);
    }

    /**
     * Searches artists with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional canonical-name query
     * @param songUuids optional song resource UUID filters
     * @param groupArtistUuids optional group artist resource UUID filters
     * @param memberArtistUuids optional member artist resource UUID filters
     * @param pageable page/sort options
     * @return sliced artist list response
     */
    @Transactional(readOnly = true)
    public ArtistListResponse search(
            ResourceStatus status,
            String query,
            List<UUID> songUuids,
            List<UUID> groupArtistUuids,
            List<UUID> memberArtistUuids,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedSongUuids = normalizeUuids(songUuids);
        List<UUID> normalizedGroupArtistUuids = normalizeUuids(groupArtistUuids);
        List<UUID> normalizedMemberArtistUuids = normalizeUuids(memberArtistUuids);

        Page<Artist> result =
                artistRepository.search(
                        new ArtistCriteria(
                                status,
                                normalizedQuery,
                                normalizedSongUuids,
                                normalizedGroupArtistUuids,
                                normalizedMemberArtistUuids),
                        pageable);

        List<ArtistElementResponse> items =
                result.getContent().stream().map(this::toSummary).toList();

        return new ArtistListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private void validateLinks(JsonNode links) {
        if (links != null && !links.isArray()) {
            throw new IllegalArgumentException("links must be a JSON array");
        }
    }

    private void updateArtistFields(Artist artist, Resource resource, ArtistUpdateRequest request) {
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
                        ? artist.getContent()
                        : normalizeNullable(request.content());
        JsonNode links = request.links() == null ? artist.getLinks() : toJsonNode(request.links());
        validateLinks(links);

        resource.updateCanonicalName(canonicalName);
        resource.updateThumbnailUrl(thumbnailUrl);
        artist.update(content, links);
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

    private List<ResourceName> saveResourceNames(
            Resource resource, List<ArtistCreateRequest.ResourceNameCreateRequest> names) {
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
            Resource resource, List<ArtistCreateRequest.ResourceAclCreateRequest> acls) {
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

    private List<ArtistGroup> saveArtistMemberships(
            Artist memberArtist, List<ArtistCreateRequest.ArtistMemberCreateRequest> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        validateNoNullItems("members", members);

        HashSet<UUID> uniqueGroupArtistUuids = new HashSet<>();
        for (ArtistCreateRequest.ArtistMemberCreateRequest member : members) {
            UUID groupArtistResourceUuid = member.groupArtistResourceUuid();
            if (!uniqueGroupArtistUuids.add(groupArtistResourceUuid)) {
                throw new IllegalArgumentException(
                        "Duplicate groupArtistResourceUuid: " + groupArtistResourceUuid);
            }
        }

        List<UUID> groupArtistUuids =
                members.stream()
                        .map(ArtistCreateRequest.ArtistMemberCreateRequest::groupArtistResourceUuid)
                        .distinct()
                        .toList();
        Map<UUID, Long> artistIdsByUuid = fetchArtistIdsByResourceUuid(groupArtistUuids);
        Map<Long, Set<Integer>> usedSortOrdersByGroupArtistId =
                loadUsedSortOrdersByGroupArtistId(new ArrayList<>(artistIdsByUuid.values()));

        List<ArtistGroup> entities =
                members.stream()
                        .map(
                                item -> {
                                    Long groupArtistId =
                                            artistIdsByUuid.get(item.groupArtistResourceUuid());
                                    if (groupArtistId == null) {
                                        throw new IllegalArgumentException(
                                                "Unknown groupArtistResourceUuid: "
                                                        + item.groupArtistResourceUuid());
                                    }
                                    if (memberArtist
                                            .getResource()
                                            .getUuid()
                                            .equals(item.groupArtistResourceUuid())) {
                                        throw new IllegalArgumentException(
                                                "groupArtist and memberArtist must be different");
                                    }
                                    int resolvedSortOrder =
                                            resolveMembershipSortOrder(
                                                    usedSortOrdersByGroupArtistId,
                                                    groupArtistId,
                                                    item.sortOrder(),
                                                    item.groupArtistResourceUuid());
                                    Artist groupArtist =
                                            entityManager.getReference(Artist.class, groupArtistId);
                                    return ArtistGroup.create(
                                            groupArtist, memberArtist, resolvedSortOrder);
                                })
                        .toList();

        return artistGroupRepository.saveAllAndFlush(entities).stream()
                .sorted(
                        Comparator.comparingInt(ArtistGroup::getSortOrder)
                                .thenComparing(ArtistGroup::getId))
                .toList();
    }

    private List<ResourceName> replaceResourceNames(
            Resource resource, List<ArtistCreateRequest.ResourceNameCreateRequest> names) {
        resourceNameRepository.deleteByResourceId(resource.getId());
        return saveResourceNames(resource, names);
    }

    private List<Acl> replaceAcls(
            Resource resource, List<ArtistCreateRequest.ResourceAclCreateRequest> acls) {
        aclRepository.deleteByResourceId(resource.getId());
        return saveAcls(resource, acls);
    }

    private List<ArtistGroup> replaceArtistMemberships(
            Artist artist, List<ArtistCreateRequest.ArtistMemberCreateRequest> members) {
        artistGroupRepository.deleteByMemberArtistId(artist.getId());
        return saveArtistMemberships(artist, members);
    }

    private List<ArtistCreateRequest.ResourceNameCreateRequest> toCreateNames(
            List<ArtistUpdateRequest.ResourceNameUpdateRequest> names) {
        return names.stream()
                .map(
                        item ->
                                new ArtistCreateRequest.ResourceNameCreateRequest(
                                        item.langCode(),
                                        item.name(),
                                        item.isPrimary(),
                                        item.sortOrder()))
                .toList();
    }

    private List<ArtistCreateRequest.ResourceAclCreateRequest> toCreateAcls(
            List<ArtistUpdateRequest.ResourceAclUpdateRequest> acls) {
        return acls.stream()
                .map(
                        item ->
                                new ArtistCreateRequest.ResourceAclCreateRequest(
                                        item.action(),
                                        item.subjectType(),
                                        item.subjectValue(),
                                        item.effect(),
                                        item.priority(),
                                        item.expiresAt()))
                .toList();
    }

    private List<ArtistCreateRequest.ArtistMemberCreateRequest> toCreateMembers(
            List<ArtistUpdateRequest.ArtistMemberUpdateRequest> members) {
        return members.stream()
                .map(
                        item ->
                                new ArtistCreateRequest.ArtistMemberCreateRequest(
                                        item.groupArtistResourceUuid(), item.sortOrder()))
                .toList();
    }

    private Map<UUID, Long> fetchArtistIdsByResourceUuid(List<UUID> resourceUuids) {
        if (resourceUuids.isEmpty()) {
            return Map.of();
        }
        List<ResourceRefProjection> refs =
                artistRepository.findResourceRefsByResourceUuids(resourceUuids);
        return refs.stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                ResourceRefProjection::getResourceUuid,
                                ResourceRefProjection::getId));
    }

    private Map<Long, Set<Integer>> loadUsedSortOrdersByGroupArtistId(List<Long> groupArtistIds) {
        if (groupArtistIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Integer>> usedSortOrdersByGroupArtistId = new HashMap<>();
        for (ArtistGroup group :
                artistGroupRepository
                        .findAllByGroupArtistIdInOrderByGroupArtistIdAscSortOrderAscIdAsc(
                                groupArtistIds)) {
            usedSortOrdersByGroupArtistId
                    .computeIfAbsent(group.getGroupArtist().getId(), ignored -> new HashSet<>())
                    .add(group.getSortOrder());
        }
        return usedSortOrdersByGroupArtistId;
    }

    private int resolveMembershipSortOrder(
            Map<Long, Set<Integer>> usedSortOrdersByGroupArtistId,
            Long groupArtistId,
            Integer requestedSortOrder,
            UUID groupArtistResourceUuid) {
        Set<Integer> usedSortOrders =
                usedSortOrdersByGroupArtistId.computeIfAbsent(
                        groupArtistId, ignored -> new HashSet<>());

        if (requestedSortOrder != null) {
            if (!usedSortOrders.add(requestedSortOrder)) {
                throw new IllegalArgumentException(
                        "Duplicate sortOrder for groupArtistResourceUuid: "
                                + groupArtistResourceUuid);
            }
            return requestedSortOrder;
        }

        int nextSortOrder = 0;
        while (usedSortOrders.contains(nextSortOrder)) {
            nextSortOrder++;
        }
        usedSortOrders.add(nextSortOrder);
        return nextSortOrder;
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

    private JsonNode buildArtistProjection(
            Artist artist,
            Resource resource,
            List<ResourceName> names,
            List<Acl> acls,
            List<ArtistGroup> groups,
            List<ArtistGroup> memberships) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("resourceUuid", resource.getUuid().toString());
        data.put("canonicalName", resource.getCanonicalName());
        data.put("status", resource.getStatus().name());
        data.put("viewCount", resource.getViewCount());
        putNullableText(data, "thumbnailUrl", resource.getThumbnailUrl());
        putNullableText(data, "content", artist.getContent());
        data.set(
                "links",
                artist.getLinks() == null ? objectMapper.createArrayNode() : artist.getLinks());
        putNullableText(data, "createdAt", formatDateTime(resource.getCreatedAt()));
        putNullableText(data, "updatedAt", formatDateTime(resource.getUpdatedAt()));
        data.set("names", buildNamesProjection(names));
        data.set("acls", buildAclsProjection(acls));
        data.set("songs", objectMapper.createArrayNode());
        data.set("groups", buildArtistGroupsProjection(groups));
        data.set("members", buildArtistMembersProjection(memberships));
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

    private ArrayNode buildArtistGroupsProjection(List<ArtistGroup> groups) {
        ArrayNode items = objectMapper.createArrayNode();
        for (ArtistGroup group : groups) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put(
                    "memberArtistResourceUuid",
                    group.getMemberArtist().getResource().getUuid().toString());
            item.put(
                    "memberArtistCanonicalName",
                    group.getMemberArtist().getResource().getCanonicalName());
            putNullableText(
                    item,
                    "memberArtistThumbnailUrl",
                    group.getMemberArtist().getResource().getThumbnailUrl());
            item.put("sortOrder", group.getSortOrder());
            items.add(item);
        }
        return items;
    }

    private ArrayNode buildArtistMembersProjection(List<ArtistGroup> memberships) {
        ArrayNode items = objectMapper.createArrayNode();
        for (ArtistGroup membership : memberships) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put(
                    "groupArtistResourceUuid",
                    membership.getGroupArtist().getResource().getUuid().toString());
            item.put(
                    "groupArtistCanonicalName",
                    membership.getGroupArtist().getResource().getCanonicalName());
            putNullableText(
                    item,
                    "groupArtistThumbnailUrl",
                    membership.getGroupArtist().getResource().getThumbnailUrl());
            item.put("sortOrder", membership.getSortOrder());
            items.add(item);
        }
        return items;
    }

    private JsonNode extractExistingArray(JsonNode data, String fieldName) {
        if (data == null || data.isNull()) {
            return objectMapper.createArrayNode();
        }
        JsonNode node = data.get(fieldName);
        return node != null && node.isArray() ? node.deepCopy() : objectMapper.createArrayNode();
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

    private ArtistElementResponse toSummary(Artist artist) {
        Resource resource = artist.getResource();
        return new ArtistElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    private JsonNode buildHistorySnapshot(Artist artist, Resource resource) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("canonicalName", resource.getCanonicalName());
        if (resource.getThumbnailUrl() == null) {
            snapshot.putNull("thumbnailUrl");
        } else {
            snapshot.put("thumbnailUrl", resource.getThumbnailUrl());
        }
        if (artist.getContent() == null) {
            snapshot.putNull("content");
        } else {
            snapshot.put("content", artist.getContent());
        }
        snapshot.set("links", toSnapshotJson(artist.getLinks()));
        snapshot.set("names", buildNamesSnapshot(resource));
        snapshot.set("acls", buildAclsSnapshot(resource));
        snapshot.set("members", buildMembersSnapshot(artist));
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

    private ArrayNode buildMembersSnapshot(Artist artist) {
        ArrayNode members = objectMapper.createArrayNode();
        for (ArtistGroup item :
                artistGroupRepository.findAllByMemberArtistIdOrderBySortOrderAscIdAsc(
                        artist.getId())) {
            ObjectNode member = objectMapper.createObjectNode();
            member.put(
                    "groupArtistResourceUuid",
                    item.getGroupArtist().getResource().getUuid().toString());
            member.put("sortOrder", item.getSortOrder());
            members.add(member);
        }
        return members;
    }

    private JsonNode toSnapshotJson(JsonNode value) {
        return value == null ? objectMapper.nullNode() : value.deepCopy();
    }
}
