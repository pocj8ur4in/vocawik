package com.vocawik.service.artist;

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
import com.vocawik.domain.artist.ArtistGroup;
import com.vocawik.domain.artist.ArtistLink;
import com.vocawik.domain.artist.ArtistLinkType;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.artist.ArtistCreateRequest;
import com.vocawik.dto.artist.ArtistElementResponse;
import com.vocawik.dto.artist.ArtistListResponse;
import com.vocawik.dto.artist.ArtistSuggestionElementResponse;
import com.vocawik.dto.artist.ArtistSuggestionListResponse;
import com.vocawik.dto.artist.ArtistUpdateRequest;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistCriteria;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistLinkRepository;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private static final int ARTIST_SUGGESTION_LIMIT = 10;

    private final ArtistRepository artistRepository;
    private final ArtistGroupRepository artistGroupRepository;
    private final ArtistLinkRepository artistLinkRepository;
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
        ArtistCreateRequest.CanonicalNameCreateRequest canonicalName = request.canonicalName();
        Artist artist =
                Artist.create(
                        normalizeCanonicalName(canonicalName.name()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()));

        Resource resource = resourceRepository.save(artist.getResource());
        artistRepository.saveAndFlush(artist);

        saveResourceNames(resource, canonicalName, request.aliases());
        saveArtistLinks(artist, request.links());
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

        if (request.canonicalName() != null || request.aliases() != null) {
            ArtistCreateRequest.CanonicalNameCreateRequest canonicalName =
                    request.canonicalName() == null
                            ? toCreateCanonical(loadCanonicalName(resource))
                            : toCreateCanonical(request.canonicalName());
            List<ArtistCreateRequest.ResourceAliasCreateRequest> aliases =
                    request.aliases() == null
                            ? toCreateAliasesFromResourceNames(loadAliases(resource))
                            : toCreateAliases(request.aliases());
            syncResourceNames(resource, canonicalName, aliases);
        }
        if (request.acls() != null) {
            syncAcls(resource, toCreateAcls(request.acls()));
        }
        if (request.links() != null) {
            syncArtistLinks(artist, request.links());
        }
        if (request.members() != null) {
            syncArtistMemberships(artist, toCreateMembers(request.members()));
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

        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceId(result.getContent());
        List<ArtistElementResponse> items =
                result.getContent().stream()
                        .map(artist -> toSummary(artist, localizedNamesByResourceId))
                        .toList();

        return new ArtistListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ArtistSuggestionListResponse suggest(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new ArtistSuggestionListResponse(List.of());
        }

        LinkedHashMap<UUID, ArtistSuggestionElementResponse> suggestionsByUuid =
                new LinkedHashMap<>();
        resourceNameRepository
                .findArtistSuggestionCandidates(
                        ResourceStatus.ACTIVE,
                        normalizedQuery,
                        org.springframework.data.domain.PageRequest.of(
                                0, ARTIST_SUGGESTION_LIMIT * 3))
                .forEach(
                        resourceName -> {
                            UUID resourceUuid = resourceName.getResource().getUuid();
                            suggestionsByUuid.putIfAbsent(
                                    resourceUuid,
                                    new ArtistSuggestionElementResponse(
                                            resourceUuid, resourceName.getName()));
                        });

        return new ArtistSuggestionListResponse(
                suggestionsByUuid.values().stream().limit(ARTIST_SUGGESTION_LIMIT).toList());
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

    private void updateArtistFields(Artist artist, Resource resource, ArtistUpdateRequest request) {
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
                        ? artist.getContent()
                        : normalizeNullable(request.content());

        resource.updateCanonicalName(canonicalName);
        resource.updateThumbnailUrl(thumbnailUrl);
        artist.update(content);
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
            Resource resource,
            ArtistCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<ArtistCreateRequest.ResourceAliasCreateRequest> aliases) {
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
        for (ArtistCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<ArtistCreateRequest.ResourceAliasCreateRequest>of()
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

    private List<ArtistLink> saveArtistLinks(
            Artist artist, List<ArtistCreateRequest.ArtistLinkCreateRequest> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }

        List<ArtistLink> entities =
                links.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "links contains null item");
                                    }
                                    return ArtistLink.create(
                                            artist,
                                            parseArtistLinkType(item.type()),
                                            normalizeLinkUrl(item.url()),
                                            normalizeNullable(item.content()),
                                            item.isDeleted());
                                })
                        .toList();

        return artistLinkRepository.saveAllAndFlush(entities).stream()
                .sorted(Comparator.comparing(ArtistLink::getId))
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

    private List<ResourceName> syncResourceNames(
            Resource resource,
            ArtistCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<ArtistCreateRequest.ResourceAliasCreateRequest> aliases) {
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

        for (ArtistCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<ArtistCreateRequest.ResourceAliasCreateRequest>of()
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
            Resource resource, List<ArtistCreateRequest.ResourceAclCreateRequest> acls) {
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
        for (ArtistCreateRequest.ResourceAclCreateRequest item : acls) {
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

    private List<ArtistLink> syncArtistLinks(
            Artist artist, List<ArtistUpdateRequest.ArtistLinkUpdateRequest> links) {
        List<ArtistLink> existingLinks =
                artistLinkRepository.findAllByArtistIdOrderByIdAsc(artist.getId());
        if (links.isEmpty()) {
            if (!existingLinks.isEmpty()) {
                artistLinkRepository.deleteAllInBatch(existingLinks);
                artistLinkRepository.flush();
            }
            return List.of();
        }

        Map<ArtistLinkKey, Deque<ArtistLink>> existingByKey = new HashMap<>();
        for (ArtistLink existing : existingLinks) {
            ArtistLinkKey key = new ArtistLinkKey(existing.getArtistLinkType(), existing.getUrl());
            existingByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(existing);
        }

        HashSet<Long> matchedIds = new HashSet<>();
        List<ArtistLink> toCreate = new ArrayList<>();
        for (ArtistUpdateRequest.ArtistLinkUpdateRequest item : links) {
            if (item == null) {
                throw new IllegalArgumentException("links contains null item");
            }
            ArtistLinkType type = parseArtistLinkType(item.type());
            String url = normalizeLinkUrl(item.url());
            ArtistLinkKey key = new ArtistLinkKey(type, url);
            Deque<ArtistLink> candidates = existingByKey.get(key);

            if (candidates != null && !candidates.isEmpty()) {
                ArtistLink matched = candidates.removeFirst();
                matched.update(normalizeNullable(item.content()), item.isDeleted());
                matchedIds.add(matched.getId());
                continue;
            }

            toCreate.add(
                    ArtistLink.create(
                            artist,
                            type,
                            url,
                            normalizeNullable(item.content()),
                            item.isDeleted()));
        }

        List<ArtistLink> toDelete =
                existingLinks.stream().filter(item -> !matchedIds.contains(item.getId())).toList();
        if (!toDelete.isEmpty()) {
            artistLinkRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            artistLinkRepository.saveAll(toCreate);
        }
        artistLinkRepository.flush();
        return artistLinkRepository.findAllByArtistIdOrderByIdAsc(artist.getId());
    }

    private List<ArtistGroup> syncArtistMemberships(
            Artist artist, List<ArtistCreateRequest.ArtistMemberCreateRequest> members) {
        List<ArtistGroup> existingMemberships =
                artistGroupRepository.findAllByMemberArtistIdOrderBySortOrderAscIdAsc(
                        artist.getId());
        if (members.isEmpty()) {
            if (!existingMemberships.isEmpty()) {
                artistGroupRepository.deleteAllInBatch(existingMemberships);
                artistGroupRepository.flush();
            }
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
            if (artist.getResource().getUuid().equals(groupArtistResourceUuid)) {
                throw new IllegalArgumentException(
                        "groupArtist and memberArtist must be different");
            }
        }

        List<UUID> groupArtistUuids =
                members.stream()
                        .map(ArtistCreateRequest.ArtistMemberCreateRequest::groupArtistResourceUuid)
                        .toList();
        Map<UUID, Long> artistIdsByUuid = fetchArtistIdsByResourceUuid(groupArtistUuids);
        for (UUID groupArtistUuid : groupArtistUuids) {
            if (!artistIdsByUuid.containsKey(groupArtistUuid)) {
                throw new IllegalArgumentException(
                        "Unknown groupArtistResourceUuid: " + groupArtistUuid);
            }
        }

        Map<Long, ArtistGroup> existingByGroupArtistId = new HashMap<>();
        for (ArtistGroup existing : existingMemberships) {
            existingByGroupArtistId.put(existing.getGroupArtist().getId(), existing);
        }

        Map<Long, Set<Integer>> usedSortOrdersByGroupArtistId =
                loadUsedSortOrdersByGroupArtistId(new ArrayList<>(artistIdsByUuid.values()));

        List<ArtistGroup> toCreate = new ArrayList<>();
        for (ArtistCreateRequest.ArtistMemberCreateRequest member : members) {
            Long groupArtistId = artistIdsByUuid.get(member.groupArtistResourceUuid());
            ArtistGroup existing = existingByGroupArtistId.remove(groupArtistId);
            int resolvedSortOrder;

            if (existing != null && member.sortOrder() == null) {
                resolvedSortOrder = existing.getSortOrder();
            } else {
                Set<Integer> usedSortOrders =
                        usedSortOrdersByGroupArtistId.computeIfAbsent(
                                groupArtistId, ignored -> new HashSet<>());
                if (existing != null) {
                    usedSortOrders.remove(existing.getSortOrder());
                }
                resolvedSortOrder =
                        resolveMembershipSortOrder(
                                usedSortOrdersByGroupArtistId,
                                groupArtistId,
                                member.sortOrder(),
                                member.groupArtistResourceUuid());
            }

            if (existing == null) {
                Artist groupArtist = entityManager.getReference(Artist.class, groupArtistId);
                toCreate.add(ArtistGroup.create(groupArtist, artist, resolvedSortOrder));
                continue;
            }
            existing.updateSortOrder(resolvedSortOrder);
        }

        if (!existingByGroupArtistId.isEmpty()) {
            artistGroupRepository.deleteAllInBatch(
                    new ArrayList<>(existingByGroupArtistId.values()));
        }
        if (!toCreate.isEmpty()) {
            artistGroupRepository.saveAll(toCreate);
        }
        artistGroupRepository.flush();
        return artistGroupRepository.findAllByMemberArtistIdOrderBySortOrderAscIdAsc(
                artist.getId());
    }

    private ArtistCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            ArtistUpdateRequest.CanonicalNameUpdateRequest canonicalName) {
        return new ArtistCreateRequest.CanonicalNameCreateRequest(
                canonicalName.langCode(), canonicalName.name());
    }

    private ArtistCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            ResourceName resourceName) {
        return new ArtistCreateRequest.CanonicalNameCreateRequest(
                resourceName.getLangCode(), resourceName.getName());
    }

    private List<ArtistCreateRequest.ResourceAliasCreateRequest> toCreateAliases(
            List<ArtistUpdateRequest.ResourceAliasUpdateRequest> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new ArtistCreateRequest.ResourceAliasCreateRequest(
                                        item.langCode(), item.name(), item.sortOrder()))
                .toList();
    }

    private List<ArtistCreateRequest.ResourceAliasCreateRequest> toCreateAliasesFromResourceNames(
            List<ResourceName> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new ArtistCreateRequest.ResourceAliasCreateRequest(
                                        item.getLangCode(), item.getName(), item.getSortOrder()))
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

    private ArtistLinkType parseArtistLinkType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("links.type is required");
        }
        // Backward-compatible alias for client typo.
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("SOUNDCLO".equals(normalized)) {
            normalized = ArtistLinkType.SOUNDCLOUD.name();
        }
        try {
            return ArtistLinkType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("links.type is invalid: " + value);
        }
    }

    private String normalizeLinkUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("links.url is required");
        }
        return url.trim();
    }

    private record ResourceNameKey(Language langCode, String name) {}

    private record DesiredResourceName(
            Language langCode, String name, boolean isPrimary, int sortOrder) {}

    private record AclKey(
            AclAction action, AclSubjectType subjectType, String subjectValue, int priority) {}

    private record ArtistLinkKey(ArtistLinkType type, String url) {}

    private <E extends Enum<E>> E parseEnum(String rawValue, Class<E> enumClass, String fieldName) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
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
        data.set("links", buildArtistLinksSnapshot(artist.getId()));
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

    private Map<Long, String> loadLocalizedNamesByResourceId(List<Artist> artists) {
        Language language = resolveCurrentLanguage();
        if (language == null || artists.isEmpty()) {
            return Map.of();
        }

        List<Long> resourceIds =
                artists.stream().map(artist -> artist.getResource().getId()).distinct().toList();

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

    private ArtistElementResponse toSummary(
            Artist artist, Map<Long, String> localizedNamesByResourceId) {
        Resource resource = artist.getResource();
        return new ArtistElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                localizedNamesByResourceId.get(resource.getId()),
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
        snapshot.set("links", buildArtistLinksSnapshot(artist.getId()));
        snapshot.set("names", buildNamesSnapshot(resource));
        snapshot.set("acls", buildAclsSnapshot(resource));
        snapshot.set("members", buildMembersSnapshot(artist));
        return snapshot;
    }

    private ArrayNode buildArtistLinksSnapshot(Long artistId) {
        ArrayNode links = objectMapper.createArrayNode();
        for (ArtistLink item : artistLinkRepository.findAllByArtistIdOrderByIdAsc(artistId)) {
            ObjectNode link = objectMapper.createObjectNode();
            link.put("type", item.getArtistLinkType().name());
            link.put("url", item.getUrl());
            putNullableText(link, "content", item.getContent());
            link.put("isDeleted", item.isDeleted());
            links.add(link);
        }
        return links;
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
