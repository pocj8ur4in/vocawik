package com.vocawik.service.debate;

import com.vocawik.domain.debate.Debate;
import com.vocawik.domain.debate.DebateStatus;
import com.vocawik.dto.debate.DebateListElementResponse;
import com.vocawik.dto.debate.DebateListResponse;
import com.vocawik.repository.debate.DebateCommentCountProjection;
import com.vocawik.repository.debate.DebateCommentRepository;
import com.vocawik.repository.debate.DebateRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for resource debate listings. */
@Service
@RequiredArgsConstructor
public class DebateService {

    private final ResourceRepository resourceRepository;
    private final DebateRepository debateRepository;
    private final DebateCommentRepository debateCommentRepository;

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

    private String resolveAuthorName(Debate debate) {
        if (debate.getActorUser() != null) {
            return debate.getActorUser().getNickname();
        }
        if (debate.getActorGuest() != null) {
            return "Guest";
        }
        return "System";
    }
}
