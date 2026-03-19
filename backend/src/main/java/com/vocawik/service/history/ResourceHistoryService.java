package com.vocawik.service.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.history.History;
import com.vocawik.domain.history.HistoryActionType;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.user.User;
import com.vocawik.dto.history.HistoryActorResponse;
import com.vocawik.dto.history.RecentChangeElementResponse;
import com.vocawik.dto.history.RecentChangeListResponse;
import com.vocawik.dto.history.ResourceHistoryDetailResponse;
import com.vocawik.dto.history.ResourceHistoryElementResponse;
import com.vocawik.repository.guest.GuestRepository;
import com.vocawik.repository.history.HistoryRepository;
import com.vocawik.repository.history.RecentChangeProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.guest.GuestPrincipal;
import com.vocawik.security.jwt.AuthPrincipal;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records snapshot-only history rows for resource revisions. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ObjectMapper is a Spring-managed infrastructure bean and is not exposed externally.")
public class ResourceHistoryService {
    private static final int MAX_RECENT_CHANGES_SIZE = 10;

    private final HistoryRepository historyRepository;
    private final ResourceRepository resourceRepository;
    private final ResourceNameRepository resourceNameRepository;
    private final UserRepository userRepository;
    private final GuestRepository guestRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public History recordCreate(Resource resource, JsonNode snapshotData) {
        int revision = resource.advanceRevision();
        History history =
                History.createSnapshot(
                        resource,
                        revision,
                        0,
                        HistoryActionType.CREATE,
                        currentUser().orElse(null),
                        currentGuest().orElse(null),
                        snapshotData,
                        contentHash(snapshotData));
        return historyRepository.save(history);
    }

    @Transactional
    public History recordUpdate(Resource resource, JsonNode snapshotData) {
        int baseRevision = resource.getRevision();
        int revision = resource.advanceRevision();
        History history =
                History.createSnapshot(
                        resource,
                        revision,
                        baseRevision,
                        HistoryActionType.UPDATE,
                        currentUser().orElse(null),
                        currentGuest().orElse(null),
                        snapshotData,
                        contentHash(snapshotData));
        return historyRepository.save(history);
    }

    @Transactional
    public History recordDelete(Resource resource, JsonNode snapshotData) {
        int baseRevision = resource.getRevision();
        int revision = resource.advanceRevision();
        History history =
                History.createSnapshot(
                        resource,
                        revision,
                        baseRevision,
                        HistoryActionType.DELETE,
                        currentUser().orElse(null),
                        currentGuest().orElse(null),
                        snapshotData,
                        contentHash(snapshotData));
        return historyRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<ResourceHistoryElementResponse> listByResourceId(Long resourceId) {
        return historyRepository
                .findAllByResourceIdOrderByRevisionDescCreatedAtDesc(resourceId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecentChangeListResponse listRecentChanges(int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_RECENT_CHANGES_SIZE);
        List<RecentChangeProjection> recentChanges =
                historyRepository.findRecentChanges(PageRequest.of(0, effectiveSize));
        Map<Long, String> localizedNamesByResourceId =
                loadLocalizedNamesByResourceId(recentChanges);
        List<RecentChangeElementResponse> items =
                recentChanges.stream()
                        .map(projection -> toRecentChange(projection, localizedNamesByResourceId))
                        .toList();
        return new RecentChangeListResponse(items, effectiveSize);
    }

    @Transactional(readOnly = true)
    public ResourceHistoryDetailResponse getByResourceUuidAndHistoryUuid(
            UUID resourceUuid, UUID historyUuid) {
        Resource resource =
                resourceRepository
                        .findByUuid(resourceUuid)
                        .orElseThrow(
                                () ->
                                        new com.vocawik.web.exception.BusinessException(
                                                com.vocawik.web.error.ErrorCode
                                                        .RESOURCE_NOT_FOUND));

        History history =
                historyRepository
                        .findByUuid(historyUuid)
                        .orElseThrow(
                                () ->
                                        new com.vocawik.web.exception.BusinessException(
                                                com.vocawik.web.error.ErrorCode
                                                        .RESOURCE_NOT_FOUND));

        if (!history.getResource().getId().equals(resource.getId())) {
            throw new com.vocawik.web.exception.BusinessException(
                    com.vocawik.web.error.ErrorCode.RESOURCE_NOT_FOUND);
        }

        return new ResourceHistoryDetailResponse(
                history.getUuid(),
                history.getRevision(),
                history.getBaseRevision(),
                history.getActionType().name(),
                toActor(history),
                history.getCreatedAt(),
                objectMapper.convertValue(history.getSnapshotData(), Object.class));
    }

    private ResourceHistoryElementResponse toSummary(History history) {
        return new ResourceHistoryElementResponse(
                history.getUuid(),
                history.getRevision(),
                history.getActionType().name(),
                toActor(history),
                history.getCreatedAt());
    }

    private HistoryActorResponse toActor(History history) {
        if (history.getActorUser() != null) {
            return new HistoryActorResponse("USER", history.getActorUser().getNickname(), null);
        }
        if (history.getActorGuest() != null) {
            return new HistoryActorResponse("GUEST", null, history.getActorGuest().getIp());
        }
        return new HistoryActorResponse("SYSTEM", null, null);
    }

    private RecentChangeElementResponse toRecentChange(
            RecentChangeProjection projection, Map<Long, String> localizedNamesByResourceId) {
        return new RecentChangeElementResponse(
                projection.getCreatedAt(),
                projection.getResourceUuid(),
                projection.getCanonicalName(),
                localizedNamesByResourceId.get(projection.getResourceId()),
                projection.getResourceType().name(),
                projection.getActionType().name(),
                projection.getActorUserNickname());
    }

    private Map<Long, String> loadLocalizedNamesByResourceId(
            List<RecentChangeProjection> recentChanges) {
        return loadLocalizedNamesByResourceIds(
                recentChanges.stream()
                        .map(RecentChangeProjection::getResourceId)
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

    private Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Optional.empty();
        }
        return userRepository.findByUuidAndIsDeletedFalse(principal.userUuid());
    }

    private Optional<Guest> currentGuest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof GuestPrincipal principal)) {
            return Optional.empty();
        }
        return guestRepository.findByUuidAndIsDeletedFalse(principal.guestUuid());
    }

    private String contentHash(JsonNode snapshotData) {
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData is required");
        }
        try {
            byte[] jsonBytes =
                    objectMapper.writeValueAsString(snapshotData).getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(jsonBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash snapshot data", e);
        }
    }
}
