package com.vocawik.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vocawik.domain.user.User;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.jwt.AuthPrincipal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class I18nServiceTest {

    private UserRepository userRepository;
    private I18nService i18nService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        i18nService = new I18nService(userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authenticated user language should override Accept-Language")
    void resolve_authenticatedUserLanguage_shouldOverrideHeader() {
        UUID userUuid = UUID.randomUUID();
        User user =
                User.create("test@example.com", "test-user", Language.EN, null, null, null, null);
        when(userRepository.findByUuidAndIsDeletedFalse(userUuid)).thenReturn(Optional.of(user));

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, "USER"), null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.8");

        Locale resolved = i18nService.resolve(request);

        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("Accept-Language should be used when user language is UND")
    void resolve_userLanguageUnd_shouldFallbackToHeader() {
        UUID userUuid = UUID.randomUUID();
        User user =
                User.create("test@example.com", "test-user", Language.UND, null, null, null, null);
        when(userRepository.findByUuidAndIsDeletedFalse(userUuid)).thenReturn(Optional.of(user));

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, "USER"), null));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ja-JP,ja;q=0.9,en;q=0.8");

        Locale resolved = i18nService.resolve(request);

        assertThat(resolved).isEqualTo(Locale.JAPANESE);
    }

    @Test
    @DisplayName("Invalid Accept-Language should fallback to default locale")
    void resolve_invalidHeader_shouldFallbackToDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "??");

        Locale resolved = i18nService.resolve(request);

        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }
}
