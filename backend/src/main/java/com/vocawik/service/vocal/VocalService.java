package com.vocawik.service.vocal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.acl.Acl;
import com.vocawik.domain.acl.AclAction;
import com.vocawik.domain.acl.AclEffect;
import com.vocawik.domain.acl.AclSubjectType;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.vocal.Vocal;
import com.vocawik.domain.vocal.VocalLink;
import com.vocawik.domain.vocal.VocalLinkType;
import com.vocawik.dto.vocal.VocalCreateRequest;
import com.vocawik.dto.vocal.VocalElementResponse;
import com.vocawik.dto.vocal.VocalListResponse;
import com.vocawik.dto.vocal.VocalSuggestionElementResponse;
import com.vocawik.dto.vocal.VocalSuggestionListResponse;
import com.vocawik.dto.vocal.VocalUpdateRequest;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.vocal.VocalCriteria;
import com.vocawik.repository.vocal.VocalLinkRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching vocals. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ObjectMapper is a Spring-managed infrastructure bean and is not exposed externally.")
public class VocalService {

    private static final int VOCAL_SUGGESTION_LIMIT = 10;

    private final VocalRepository vocalRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final VocalLinkRepository vocalLinkRepository;
    private final AclRepository aclRepository;
    private final ResourceHistoryService resourceHistoryService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a vocal.
     *
     * @param request create payload
     * @return created vocal resource UUID
     */
    @Transactional
    public UUID create(VocalCreateRequest request) {
        VocalCreateRequest.CanonicalNameCreateRequest canonicalName = request.canonicalName();
        Vocal vocal =
                Vocal.create(
                        normalizeCanonicalName(canonicalName.name()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()),
                        null);

        Resource resource = resourceRepository.save(vocal.getResource());
        vocalRepository.save(vocal);

        saveResourceNames(resource, canonicalName, request.aliases());
        saveVocalLinks(vocal, request.links());
        saveAcls(resource, request.acls());

        resourceHistoryService.recordCreate(resource, buildHistorySnapshot(vocal, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Updates a vocal and optionally replaces child collections.
     *
     * @param resourceUuid vocal resource UUID
     * @param request update payload
     * @return updated vocal resource UUID
     */
    @Transactional
    public UUID update(UUID resourceUuid, VocalUpdateRequest request) {
        Vocal vocal =
                vocalRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = vocal.getResource();
        updateVocalFields(vocal, resource, request);

        if (request.canonicalName() != null || request.aliases() != null) {
            VocalCreateRequest.CanonicalNameCreateRequest canonicalName =
                    request.canonicalName() == null
                            ? toCreateCanonical(loadCanonicalName(resource))
                            : toCreateCanonical(request.canonicalName());
            List<VocalCreateRequest.ResourceAliasCreateRequest> aliases =
                    request.aliases() == null
                            ? toCreateAliasesFromResourceNames(loadAliases(resource))
                            : toCreateAliases(request.aliases());
            syncResourceNames(resource, canonicalName, aliases);
        }
        if (request.acls() != null) {
            syncAcls(resource, toCreateAcls(request.acls()));
        }
        if (request.links() != null) {
            syncVocalLinks(vocal, request.links());
        }

        resourceHistoryService.recordUpdate(resource, buildHistorySnapshot(vocal, resource));
        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Soft-deletes a vocal and records delete history.
     *
     * @param resourceUuid vocal resource UUID
     */
    @Transactional
    public void delete(UUID resourceUuid) {
        Vocal vocal =
                vocalRepository
                        .findByResourceUuidAndResourceIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        Resource resource = vocal.getResource();
        JsonNode snapshot = buildHistorySnapshot(vocal, resource);

        resource.softDelete();
        resourceHistoryService.recordDelete(resource, snapshot);
        resourceRepository.saveAndFlush(resource);
    }

    /**
     * Searches vocals with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional name query
     * @param songUuids optional song resource UUID filters
     * @param pageable page/sort options
     * @return sliced vocal list response
     */
    @Transactional(readOnly = true)
    public VocalListResponse search(
            ResourceStatus status, String query, List<UUID> songUuids, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedSongUuids = normalizeUuids(songUuids);

        Page<Vocal> result =
                vocalRepository.search(
                        new VocalCriteria(status, normalizedQuery, normalizedSongUuids), pageable);

        List<VocalElementResponse> items =
                result.getContent().stream().map(this::toSummary).toList();

        return new VocalListResponse(
                items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public VocalSuggestionListResponse suggest(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery == null) {
            return new VocalSuggestionListResponse(List.of());
        }

        LinkedHashMap<UUID, VocalSuggestionElementResponse> suggestionsByUuid =
                new LinkedHashMap<>();
        resourceNameRepository
                .findVocalSuggestionCandidates(
                        ResourceStatus.ACTIVE,
                        normalizedQuery,
                        org.springframework.data.domain.PageRequest.of(
                                0, VOCAL_SUGGESTION_LIMIT * 3))
                .forEach(
                        resourceName -> {
                            UUID resourceUuid = resourceName.getResource().getUuid();
                            suggestionsByUuid.putIfAbsent(
                                    resourceUuid,
                                    new VocalSuggestionElementResponse(
                                            resourceUuid, resourceName.getName()));
                        });

        return new VocalSuggestionListResponse(
                suggestionsByUuid.values().stream().limit(VOCAL_SUGGESTION_LIMIT).toList());
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
            VocalCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<VocalCreateRequest.ResourceAliasCreateRequest> aliases) {
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
        for (VocalCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<VocalCreateRequest.ResourceAliasCreateRequest>of()
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

    private List<VocalLink> saveVocalLinks(
            Vocal vocal, List<VocalCreateRequest.VocalLinkCreateRequest> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }

        List<VocalLink> entities =
                links.stream()
                        .map(
                                item -> {
                                    if (item == null) {
                                        throw new IllegalArgumentException(
                                                "links contains null item");
                                    }
                                    return VocalLink.create(
                                            vocal,
                                            parseVocalLinkType(item.type()),
                                            normalizeLinkUrl(item.url()),
                                            item.isDeleted());
                                })
                        .toList();

        return vocalLinkRepository.saveAllAndFlush(entities).stream()
                .sorted(Comparator.comparing(VocalLink::getId))
                .toList();
    }

    private List<VocalLink> syncVocalLinks(
            Vocal vocal, List<VocalUpdateRequest.VocalLinkUpdateRequest> links) {
        List<VocalLink> existingLinks =
                vocalLinkRepository.findAllByVocalIdOrderByIdAsc(vocal.getId());
        if (links.isEmpty()) {
            if (!existingLinks.isEmpty()) {
                vocalLinkRepository.deleteAllInBatch(existingLinks);
                vocalLinkRepository.flush();
            }
            return List.of();
        }

        Map<VocalLinkKey, Deque<VocalLink>> existingByKey = new HashMap<>();
        for (VocalLink existing : existingLinks) {
            VocalLinkKey key = new VocalLinkKey(existing.getVocalLinkType(), existing.getUrl());
            existingByKey.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(existing);
        }

        HashSet<Long> matchedIds = new HashSet<>();
        List<VocalLink> toCreate = new ArrayList<>();
        for (VocalUpdateRequest.VocalLinkUpdateRequest item : links) {
            if (item == null) {
                throw new IllegalArgumentException("links contains null item");
            }
            VocalLinkType type = parseVocalLinkType(item.type());
            String url = normalizeLinkUrl(item.url());
            VocalLinkKey key = new VocalLinkKey(type, url);
            Deque<VocalLink> candidates = existingByKey.get(key);

            if (candidates != null && !candidates.isEmpty()) {
                VocalLink matched = candidates.removeFirst();
                matched.updateDeleted(item.isDeleted());
                matchedIds.add(matched.getId());
                continue;
            }

            toCreate.add(VocalLink.create(vocal, type, url, item.isDeleted()));
        }

        List<VocalLink> toDelete =
                existingLinks.stream().filter(item -> !matchedIds.contains(item.getId())).toList();
        if (!toDelete.isEmpty()) {
            vocalLinkRepository.deleteAllInBatch(toDelete);
        }
        if (!toCreate.isEmpty()) {
            vocalLinkRepository.saveAll(toCreate);
        }
        vocalLinkRepository.flush();
        return vocalLinkRepository.findAllByVocalIdOrderByIdAsc(vocal.getId());
    }

    private List<Acl> saveAcls(
            Resource resource, List<VocalCreateRequest.ResourceAclCreateRequest> acls) {
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

    private void updateVocalFields(Vocal vocal, Resource resource, VocalUpdateRequest request) {
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
                        ? vocal.getContent()
                        : normalizeNullable(request.content());

        resource.updateCanonicalName(canonicalName);
        resource.updateThumbnailUrl(thumbnailUrl);
        vocal.update(content, vocal.getLinks());
    }

    private List<ResourceName> syncResourceNames(
            Resource resource,
            VocalCreateRequest.CanonicalNameCreateRequest canonicalName,
            List<VocalCreateRequest.ResourceAliasCreateRequest> aliases) {
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

        for (VocalCreateRequest.ResourceAliasCreateRequest alias :
                (aliases == null
                        ? List.<VocalCreateRequest.ResourceAliasCreateRequest>of()
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
            resourceNameRepository.deleteAllInBatch(existingByKey.values());
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
            Resource resource, List<VocalCreateRequest.ResourceAclCreateRequest> acls) {
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
        for (VocalCreateRequest.ResourceAclCreateRequest item : acls) {
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
            aclRepository.deleteAllInBatch(existingByKey.values());
        }
        if (!toCreate.isEmpty()) {
            aclRepository.saveAll(toCreate);
        }
        aclRepository.flush();
        return aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resource.getId());
    }

    private VocalCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            VocalUpdateRequest.CanonicalNameUpdateRequest canonicalName) {
        return new VocalCreateRequest.CanonicalNameCreateRequest(
                canonicalName.langCode(), canonicalName.name());
    }

    private VocalCreateRequest.CanonicalNameCreateRequest toCreateCanonical(
            ResourceName resourceName) {
        return new VocalCreateRequest.CanonicalNameCreateRequest(
                resourceName.getLangCode(), resourceName.getName());
    }

    private List<VocalCreateRequest.ResourceAliasCreateRequest> toCreateAliases(
            List<VocalUpdateRequest.ResourceAliasUpdateRequest> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new VocalCreateRequest.ResourceAliasCreateRequest(
                                        item.langCode(), item.name(), item.sortOrder()))
                .toList();
    }

    private List<VocalCreateRequest.ResourceAliasCreateRequest> toCreateAliasesFromResourceNames(
            List<ResourceName> aliases) {
        return aliases.stream()
                .map(
                        item ->
                                new VocalCreateRequest.ResourceAliasCreateRequest(
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

    private List<VocalCreateRequest.ResourceAclCreateRequest> toCreateAcls(
            List<VocalUpdateRequest.ResourceAclUpdateRequest> acls) {
        return acls.stream()
                .map(
                        item ->
                                new VocalCreateRequest.ResourceAclCreateRequest(
                                        item.action(),
                                        item.subjectType(),
                                        item.subjectValue(),
                                        item.effect(),
                                        item.priority(),
                                        item.expiresAt()))
                .toList();
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

    private VocalLinkType parseVocalLinkType(String value) {
        return parseEnum(value, VocalLinkType.class, "links.type");
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

    private record VocalLinkKey(VocalLinkType type, String url) {}

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

    private JsonNode buildVocalProjection(
            Vocal vocal, Resource resource, List<ResourceName> names, List<Acl> acls) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("resourceUuid", resource.getUuid().toString());
        data.put("canonicalName", resource.getCanonicalName());
        data.put("status", resource.getStatus().name());
        data.put("viewCount", resource.getViewCount());
        putNullableText(data, "thumbnailUrl", resource.getThumbnailUrl());
        putNullableText(data, "content", vocal.getContent());
        data.set("links", buildVocalLinksSnapshot(vocal.getId()));
        putNullableText(data, "createdAt", formatDateTime(resource.getCreatedAt()));
        putNullableText(data, "updatedAt", formatDateTime(resource.getUpdatedAt()));
        data.set("names", buildNamesProjection(names));
        data.set("acls", buildAclsProjection(acls));
        data.set("songs", objectMapper.createArrayNode());
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

    private VocalElementResponse toSummary(Vocal vocal) {
        Resource resource = vocal.getResource();
        return new VocalElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }

    private JsonNode buildHistorySnapshot(Vocal vocal, Resource resource) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("canonicalName", resource.getCanonicalName());
        if (resource.getThumbnailUrl() == null) {
            snapshot.putNull("thumbnailUrl");
        } else {
            snapshot.put("thumbnailUrl", resource.getThumbnailUrl());
        }
        if (vocal.getContent() == null) {
            snapshot.putNull("content");
        } else {
            snapshot.put("content", vocal.getContent());
        }
        snapshot.set("links", buildVocalLinksSnapshot(vocal.getId()));
        snapshot.set("names", buildNamesSnapshot(resource));
        snapshot.set("acls", buildAclsSnapshot(resource));
        return snapshot;
    }

    private ArrayNode buildVocalLinksSnapshot(Long vocalId) {
        ArrayNode links = objectMapper.createArrayNode();
        for (VocalLink item : vocalLinkRepository.findAllByVocalIdOrderByIdAsc(vocalId)) {
            ObjectNode link = objectMapper.createObjectNode();
            link.put("type", item.getVocalLinkType().name());
            link.put("url", item.getUrl());
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
}
