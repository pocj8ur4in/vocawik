package com.vocawik.service.debate;

import com.vocawik.domain.debate.Debate;
import com.vocawik.domain.debate.DebateComment;
import com.vocawik.domain.debate.DebateStatus;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.user.User;
import com.vocawik.dto.debate.DebateCreateRequest;
import com.vocawik.dto.debate.DebateListElementResponse;
import com.vocawik.dto.debate.DebateListResponse;
import com.vocawik.repository.debate.DebateCommentCountProjection;
import com.vocawik.repository.debate.DebateCommentRepository;
import com.vocawik.repository.debate.DebateRepository;
import com.vocawik.repository.guest.GuestRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.guest.GuestPrincipal;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for resource debate listings. */
@Service
@RequiredArgsConstructor
public class DebateService {

    private final ResourceRepository resourceRepository;
    private final DebateRepository debateRepository;
    private final DebateCommentRepository debateCommentRepository;
    private final UserRepository userRepository;
    private final GuestRepository guestRepository;

    @Transactional(readOnly = true)
    public DebateListResponse listByResourceUuid(UUID resourceUuid) {
        var resource =
                resourceRepository
                        .findByUuidAndIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<Debate> debates =
                debateRepository
                        .findAllByResourceIdAndIsDeletedFalseAndStatusNotOrderByCreatedAtDescIdDesc(
                                resource.getId(), DebateStatus.ARCHIVED);
        if (debates.isEmpty()) {
            return new DebateListResponse(List.of());
        }

        Map<Long, Long> commentCountByDebateId =
                debateCommentRepository
                        .countActiveCommentsByDebateIds(
                                debates.stream().map(Debate::getId).toList())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        DebateCommentCountProjection::getDebateId,
                                        DebateCommentCountProjection::getCommentCount));

        return new DebateListResponse(
                debates.stream()
                        .map(
                                debate ->
                                        new DebateListElementResponse(
                                                debate.getUuid(),
                                                debate.getTitle(),
                                                resolveAuthorName(debate),
                                                debate.getStatus().name(),
                                                debate.getCreatedAt(),
                                                commentCountByDebateId.getOrDefault(
                                                        debate.getId(), 0L)))
                        .toList());
    }

    @Transactional
    public DebateListElementResponse create(UUID resourceUuid, DebateCreateRequest request) {
        var resource =
                resourceRepository
                        .findByUuidAndIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        User actorUser = currentUser().orElse(null);
        Guest actorGuest = actorUser == null ? currentGuest().orElse(null) : null;
        if ((actorUser == null) == (actorGuest == null)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Debate debate =
                debateRepository.save(
                        Debate.create(resource, actorUser, actorGuest, request.title().trim()));
        debateCommentRepository.save(
                DebateComment.create(
                        debate, null, actorUser, actorGuest, request.content().trim()));
        return new DebateListElementResponse(
                debate.getUuid(),
                debate.getTitle(),
                resolveAuthorName(debate),
                debate.getStatus().name(),
                debate.getCreatedAt(),
                1L);
    }

    private String resolveAuthorName(Debate debate) {
        if (debate.getActorUser() != null) {
            return debate.getActorUser().getNickname();
        }
        if (debate.getActorGuest() != null) {
            return "Guest";
        }
        return "System";
    }

    private Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof AuthPrincipal authPrincipal)) {
            return Optional.empty();
        }
        return userRepository.findByUuidAndIsDeletedFalse(authPrincipal.userUuid());
    }

    private Optional<Guest> currentGuest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof GuestPrincipal guestPrincipal)) {
            return Optional.empty();
        }
        return guestRepository.findByUuidAndIsDeletedFalse(guestPrincipal.guestUuid());
    }
}
