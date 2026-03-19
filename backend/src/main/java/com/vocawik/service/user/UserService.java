package com.vocawik.service.user;

import com.vocawik.domain.user.User;
import com.vocawik.dto.user.UserMeResponse;
import com.vocawik.dto.user.UserProfileResponse;
import com.vocawik.repository.history.HistoryRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import com.vocawik.web.exception.UnauthorizedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for user-facing profile operations. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final HistoryRepository historyRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(UUID userUuid) {
        User user =
                userRepository
                        .findByUuidAndIsDeletedFalse(userUuid)
                        .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        return new UserMeResponse(
                user.getNickname(),
                user.getLangCode().name(),
                user.getTimezone().getId(),
                user.getTheme().name(),
                user.getPvProvider().name(),
                user.getLastLoginAt());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userUuid) {
        User user =
                userRepository
                        .findByUuidAndIsDeletedFalse(userUuid)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.RESOURCE_NOT_FOUND, "User not found."));

        return new UserProfileResponse(
                user.getUuid(),
                user.getNickname(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                historyRepository.countByActorUserId(user.getId()));
    }
}
