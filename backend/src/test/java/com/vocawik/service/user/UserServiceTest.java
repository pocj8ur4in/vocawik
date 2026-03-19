package com.vocawik.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserPvProvider;
import com.vocawik.domain.user.UserTheme;
import com.vocawik.dto.user.UserMeResponse;
import com.vocawik.repository.user.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("Get current user should return only profile preference fields")
    void getCurrentUser_shouldReturnOnlyProfilePreferenceFields() {
        UUID userUuid = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getNickname()).thenReturn("string");
        when(user.getLangCode()).thenReturn(Language.EN);
        when(user.getTimezone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(user.getTheme()).thenReturn(UserTheme.LIGHT);
        when(user.getPvProvider()).thenReturn(UserPvProvider.YOUTUBE);
        when(user.getLastLoginAt()).thenReturn(LocalDateTime.parse("2026-03-19T12:45:43.445"));
        when(userRepository.findByUuidAndIsDeletedFalse(eq(userUuid)))
                .thenReturn(Optional.of(user));

        UserMeResponse result = userService.getCurrentUser(userUuid);

        assertThat(result)
                .isEqualTo(
                        new UserMeResponse(
                                "string",
                                "EN",
                                "Asia/Seoul",
                                "LIGHT",
                                "YOUTUBE",
                                LocalDateTime.parse("2026-03-19T12:45:43.445")));
    }
}
