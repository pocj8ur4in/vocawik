package com.vocawik.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vocawik.exception.UnauthorizedException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserArgumentResolverTest {

    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentUserArgumentResolver();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should support @CurrentUser UUID parameter")
    void supportsParameter_withCurrentUserUuid_shouldReturnTrue() {
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.hasParameterAnnotation(CurrentUser.class)).thenReturn(true);
        doReturn(UUID.class).when(parameter).getParameterType();

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    @DisplayName("Should not support parameter without @CurrentUser")
    void supportsParameter_withoutAnnotation_shouldReturnFalse() {
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.hasParameterAnnotation(CurrentUser.class)).thenReturn(false);
        doReturn(UUID.class).when(parameter).getParameterType();

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("Should not support @CurrentUser String parameter")
    void supportsParameter_withWrongType_shouldReturnFalse() {
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.hasParameterAnnotation(CurrentUser.class)).thenReturn(true);
        doReturn(String.class).when(parameter).getParameterType();

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    @DisplayName("Should resolve user UUID from SecurityContext")
    void resolveArgument_withAuthentication_shouldReturnUserUuid() {
        MethodParameter parameter = mock(MethodParameter.class);
        doReturn(UUID.class).when(parameter).getParameterType();
        UUID userUuid = UUID.randomUUID();
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userUuid.toString(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Object result = resolver.resolveArgument(parameter, null, null, null);

        assertThat(result).isEqualTo(userUuid);
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when no authentication")
    void resolveArgument_withNoAuth_shouldThrow() {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException for anonymous user")
    void resolveArgument_withAnonymousUser_shouldThrow() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("Should throw UnauthorizedException for invalid UUID subject")
    void resolveArgument_withInvalidUuidSubject_shouldThrow() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid authentication subject");
    }
}
