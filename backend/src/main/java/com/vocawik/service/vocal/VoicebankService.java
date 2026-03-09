package com.vocawik.service.vocal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vocawik.domain.acl.Acl;
import com.vocawik.domain.acl.AclAction;
import com.vocawik.domain.acl.AclEffect;
import com.vocawik.domain.acl.AclSubjectType;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.vocal.VocalCharacter;
import com.vocawik.domain.vocal.VocalVoicebank;
import com.vocawik.domain.vocal.VoicebankType;
import com.vocawik.dto.voicebank.VoicebankCreateRequest;
import com.vocawik.dto.voicebank.VoicebankElementResponse;
import com.vocawik.dto.voicebank.VoicebankListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.common.ResourceRefProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.vocal.VocalCharacterRepository;
import com.vocawik.repository.vocal.VocalVoicebankRepository;
import com.vocawik.repository.vocal.VoicebankCriteria;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching voicebanks. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ObjectMapper is a Spring-managed infrastructure bean and is not exposed externally.")
public class VoicebankService {

    private final VocalVoicebankRepository vocalVoicebankRepository;
    private final VocalCharacterRepository vocalCharacterRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final AclRepository aclRepository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    /**
     * Creates a voicebank and initializes resource projection payload.
     *
     * @param request create payload
     * @return created voicebank resource UUID
     */
    @Transactional
    public UUID create(VoicebankCreateRequest request) {
        JsonNode links = toJsonNode(request.links());
        validateLinks(links);

        Long vocalCharacterId = fetchVocalCharacterId(request.vocalCharacterResourceUuid());
        VocalCharacter vocalCharacter =
                entityManager.getReference(VocalCharacter.class, vocalCharacterId);

        VocalVoicebank voicebank =
                VocalVoicebank.create(
                        normalizeCanonicalName(request.canonicalName()),
                        normalizeNullable(request.thumbnailUrl()),
                        normalizeNullable(request.content()),
                        links,
                        vocalCharacter,
                        parseVoicebankType(request.voicebankType()));

        Resource resource = resourceRepository.save(voicebank.getResource());
        vocalVoicebankRepository.save(voicebank);

        saveResourceNames(resource, request.names());
        saveAcls(resource, request.acls());

        resourceRepository.saveAndFlush(resource);
        return resource.getUuid();
    }

    /**
     * Searches voicebanks with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional name query
     * @param songUuids optional song resource UUID filters
     * @param vocalUuids optional vocal resource UUID filters
     * @param voicebankTypes optional voicebank type filters
     * @param pageable page/sort options
     * @return sliced voicebank list response
     */
    @Transactional(readOnly = true)
    public VoicebankListResponse search(
            ResourceStatus status,
            String query,
            List<UUID> songUuids,
            List<UUID> vocalUuids,
            List<VoicebankType> voicebankTypes,
            Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        List<UUID> normalizedSongUuids = normalizeUuids(songUuids);
        List<UUID> normalizedVocalUuids = normalizeUuids(vocalUuids);
        List<VoicebankType> normalizedVoicebankTypes = normalizeVoicebankTypes(voicebankTypes);

        Slice<VocalVoicebank> resultSlice =
                vocalVoicebankRepository.search(
                        new VoicebankCriteria(
                                status,
                                normalizedQuery,
                                normalizedSongUuids,
                                normalizedVocalUuids,
                                normalizedVoicebankTypes),
                        pageable);

        List<VoicebankElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new VoicebankListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
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

    private List<VoicebankType> normalizeVoicebankTypes(List<VoicebankType> voicebankTypes) {
        if (voicebankTypes == null || voicebankTypes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<VoicebankType> normalizedSet = new LinkedHashSet<>();
        for (VoicebankType voicebankType : voicebankTypes) {
            if (voicebankType == null) {
                throw new IllegalArgumentException("voicebankTypes filter contains null");
            }
            normalizedSet.add(voicebankType);
        }
        return List.copyOf(normalizedSet);
    }

    private List<ResourceName> saveResourceNames(
            Resource resource, List<VoicebankCreateRequest.ResourceNameCreateRequest> names) {
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
            Resource resource, List<VoicebankCreateRequest.ResourceAclCreateRequest> acls) {
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

    private Long fetchVocalCharacterId(UUID vocalCharacterResourceUuid) {
        List<ResourceRefProjection> refs =
                vocalCharacterRepository.findResourceRefsByResourceUuids(
                        List.of(vocalCharacterResourceUuid));
        if (refs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown vocalCharacterResourceUuid: " + vocalCharacterResourceUuid);
        }
        return refs.getFirst().getId();
    }

    private VoicebankType parseVoicebankType(String value) {
        return parseEnum(value, VoicebankType.class, "voicebankType");
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

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
    }

    private JsonNode buildVoicebankProjection(
            VocalVoicebank voicebank,
            Resource resource,
            List<ResourceName> names,
            List<Acl> acls,
            VocalCharacter vocalCharacter) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("resourceUuid", resource.getUuid().toString());
        data.put("canonicalName", resource.getCanonicalName());
        data.put("status", resource.getStatus().name());
        data.put("viewCount", resource.getViewCount());
        putNullableText(data, "thumbnailUrl", resource.getThumbnailUrl());
        data.put("vocalResourceUuid", vocalCharacter.getResource().getUuid().toString());
        data.put("vocalCanonicalName", vocalCharacter.getResource().getCanonicalName());
        data.put("voicebankType", voicebank.getVoicebankType().name());
        putNullableText(data, "content", voicebank.getContent());
        data.set(
                "links",
                voicebank.getLinks() == null
                        ? objectMapper.createArrayNode()
                        : voicebank.getLinks());
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

    private VoicebankElementResponse toSummary(VocalVoicebank voicebank) {
        Resource resource = voicebank.getResource();
        return new VoicebankElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getStatus().name(),
                voicebank.getVoicebankType().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
