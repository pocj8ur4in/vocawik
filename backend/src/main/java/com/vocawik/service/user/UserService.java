package com.vocawik.service.user;

import com.vocawik.domain.user.User;
import com.vocawik.dto.user.UserMeResponse;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.web.exception.UnauthorizedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for user-facing profile operations. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserMeResponse getCurrentUser(UUID userUuid) {
        User user =
                userRepository
                        .findByUuidAndIsDeletedFalse(userUuid)
                        .orElseThrow(() -> new UnauthorizedException("Authentication required."));

        return new UserMeResponse(
                user.getUuid(),
                user.getEmail(),
                user.getNickname(),
                user.getStatus().name(),
                user.getRole().name(),
                user.getLangCode().name(),
                user.getTimezone().getId(),
                user.getTheme().name(),
                user.getPvProvider().name(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
