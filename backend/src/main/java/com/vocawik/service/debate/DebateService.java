package com.vocawik.service.debate;

import com.vocawik.domain.debate.Debate;
import com.vocawik.domain.debate.DebateComment;
import com.vocawik.domain.debate.DebateStatus;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserRole;
import com.vocawik.dto.debate.DebateCommentCreateRequest;
import com.vocawik.dto.debate.DebateCommentResponse;
import com.vocawik.dto.debate.DebateCommentUpdateRequest;
import com.vocawik.dto.debate.DebateCreateRequest;
import com.vocawik.dto.debate.DebateDetailResponse;
import com.vocawik.dto.debate.DebateListElementResponse;
import com.vocawik.dto.debate.DebateListResponse;
import com.vocawik.dto.debate.DebateStatusResponse;
import com.vocawik.dto.debate.DebateStatusUpdateRequest;
import com.vocawik.repository.debate.DebateCommentCountProjection;
import com.vocawik.repository.debate.DebateCommentRepository;
import com.vocawik.repository.debate.DebateRepository;
import com.vocawik.repository.guest.GuestRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.guest.GuestPrincipal;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.service.acl.AclPermissionService;
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
    private final AclPermissionService aclPermissionService;

    @Transactional(readOnly = true)
    public DebateListResponse listByResourceUuid(UUID resourceUuid) {
        var resource =
                resourceRepository
                        .findByUuidAndIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        aclPermissionService.assertCanRead(resource);

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

    @Transactional(readOnly = true)
    public DebateDetailResponse getByResourceUuidAndDebateUuid(UUID resourceUuid, UUID debateUuid) {
        Debate debate =
                debateRepository
                        .findByUuidAndResourceUuidAndIsDeletedFalse(debateUuid, resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        aclPermissionService.assertCanRead(debate.getResource());
        List<DebateComment> comments =
                debateCommentRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(debate.getId());

        DebateDetailResponse.Body body =
                comments.isEmpty() ? null : toDebateBody(comments.getFirst());
        List<DebateDetailResponse.Comment> replies =
                comments.stream().skip(1).map(this::toDebateComment).toList();

        return new DebateDetailResponse(
                debate.getUuid(),
                debate.getTitle(),
                resolveAuthorName(debate),
                debate.getStatus().name(),
                debate.getCreatedAt(),
                body,
                replies);
    }

    @Transactional
    public DebateListElementResponse create(UUID resourceUuid, DebateCreateRequest request) {
        var resource =
                resourceRepository
                        .findByUuidAndIsDeletedFalse(resourceUuid)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        aclPermissionService.assertCanCreateDebate(resource);
        User actorUser = currentUser().orElse(null);
        Guest actorGuest = actorUser == null ? currentGuest().orElse(null) : null;
        validateExactlyOneActor(actorUser, actorGuest);

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

    @Transactional
    public DebateCommentResponse createComment(
            UUID resourceUuid, UUID debateUuid, DebateCommentCreateRequest request) {
        Debate debate = findDebate(resourceUuid, debateUuid);
        aclPermissionService.assertCanCommentDebate(debate.getResource());
        User actorUser = currentUser().orElse(null);
        Guest actorGuest = actorUser == null ? currentGuest().orElse(null) : null;
        validateExactlyOneActor(actorUser, actorGuest);

        DebateComment parentComment =
                request.parentCommentUuid() == null
                        ? null
                        : debateCommentRepository
                                .findByUuidAndDebateIdAndIsDeletedFalse(
                                        request.parentCommentUuid(), debate.getId())
                                .orElseThrow(
                                        () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        DebateComment saved =
                debateCommentRepository.save(
                        DebateComment.create(
                                debate,
                                parentComment,
                                actorUser,
                                actorGuest,
                                request.content().trim()));
        return toDebateCommentResponse(saved);
    }

    @Transactional
    public DebateCommentResponse updateComment(
            UUID resourceUuid,
            UUID debateUuid,
            UUID commentUuid,
            DebateCommentUpdateRequest request) {
        Debate debate = findDebate(resourceUuid, debateUuid);
        DebateComment comment =
                debateCommentRepository
                        .findByUuidAndDebateIdAndIsDeletedFalse(commentUuid, debate.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!canManage(comment)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.revise(request.content().trim());
        return toDebateCommentResponse(comment);
    }

    @Transactional
    public void deleteComment(UUID resourceUuid, UUID debateUuid, UUID commentUuid) {
        Debate debate = findDebate(resourceUuid, debateUuid);
        DebateComment comment =
                debateCommentRepository
                        .findByUuidAndDebateIdAndIsDeletedFalse(commentUuid, debate.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!canManage(comment)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        comment.softDelete();
    }

    @Transactional
    public void delete(UUID resourceUuid, UUID debateUuid) {
        Debate debate = findDebate(resourceUuid, debateUuid);
        if (!canDelete(debate)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        debate.softDelete();
    }

    @Transactional
    public DebateStatusResponse updateStatus(
            UUID resourceUuid, UUID debateUuid, DebateStatusUpdateRequest request) {
        Debate debate = findDebate(resourceUuid, debateUuid);
        if (!canDelete(debate)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        DebateStatus nextStatus;
        try {
            nextStatus = DebateStatus.valueOf(request.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (nextStatus == DebateStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (nextStatus == DebateStatus.CLOSED) {
            debate.close();
        } else {
            debate.reopen();
        }

        return new DebateStatusResponse(debate.getUuid(), debate.getStatus().name());
    }

    private Debate findDebate(UUID resourceUuid, UUID debateUuid) {
        return debateRepository
                .findByUuidAndResourceUuidAndIsDeletedFalse(debateUuid, resourceUuid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void validateExactlyOneActor(User actorUser, Guest actorGuest) {
        if ((actorUser == null) == (actorGuest == null)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
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

    private DebateDetailResponse.Body toDebateBody(DebateComment comment) {
        return new DebateDetailResponse.Body(
                comment.getUuid(),
                resolveAuthorName(comment),
                comment.getContent(),
                comment.getRevision(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.isDeleted());
    }

    private DebateDetailResponse.Comment toDebateComment(DebateComment comment) {
        return new DebateDetailResponse.Comment(
                comment.getUuid(),
                comment.getParentComment() == null ? null : comment.getParentComment().getUuid(),
                resolveAuthorName(comment),
                comment.getContent(),
                comment.getRevision(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.isDeleted());
    }

    private DebateCommentResponse toDebateCommentResponse(DebateComment comment) {
        return new DebateCommentResponse(
                comment.getUuid(),
                comment.getParentComment() == null ? null : comment.getParentComment().getUuid(),
                resolveAuthorName(comment),
                comment.getContent(),
                comment.getRevision(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.isDeleted());
    }

    private String resolveAuthorName(DebateComment comment) {
        if (comment.getActorUser() != null) {
            return comment.getActorUser().getNickname();
        }
        if (comment.getActorGuest() != null) {
            return "Guest";
        }
        return "System";
    }

    private boolean canDelete(Debate debate) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            if (UserRole.ADMIN.name().equals(authPrincipal.role())) {
                return true;
            }
            return debate.getActorUser() != null
                    && authPrincipal.userUuid().equals(debate.getActorUser().getUuid());
        }
        if (principal instanceof GuestPrincipal guestPrincipal) {
            return debate.getActorGuest() != null
                    && guestPrincipal.guestUuid().equals(debate.getActorGuest().getUuid());
        }
        return false;
    }

    private boolean canManage(DebateComment comment) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            if (UserRole.ADMIN.name().equals(authPrincipal.role())) {
                return true;
            }
            return comment.getActorUser() != null
                    && authPrincipal.userUuid().equals(comment.getActorUser().getUuid());
        }
        if (principal instanceof GuestPrincipal guestPrincipal) {
            return comment.getActorGuest() != null
                    && guestPrincipal.guestUuid().equals(comment.getActorGuest().getUuid());
        }
        return false;
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
