package com.vocawik.module.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vocawik.module.security.jwt.AuthPrincipal;
import com.vocawik.module.web.error.BusinessException;
import com.vocawik.module.web.error.ErrorCode;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserArgumentResolverTest {

    private final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should support CurrentUser UUID parameter")
    void supportsParameter_withCurrentUserUuid_shouldReturnTrue() throws Exception {
        assertThat(resolver.supportsParameter(methodParameter("currentUserUuid", UUID.class)))
                .isTrue();
    }

    @Test
    @DisplayName("Should not support parameter without CurrentUser")
    void supportsParameter_withoutCurrentUser_shouldReturnFalse() throws Exception {
        assertThat(resolver.supportsParameter(methodParameter("plainUuid", UUID.class))).isFalse();
    }

    @Test
    @DisplayName("Should not support CurrentUser non-UUID parameter")
    void supportsParameter_withCurrentUserString_shouldReturnFalse() throws Exception {
        assertThat(resolver.supportsParameter(methodParameter("currentUserString", String.class)))
                .isFalse();
    }

    @Test
    @DisplayName("Should resolve UUID from AuthPrincipal")
    void resolveArgument_withAuthPrincipal_shouldReturnUserUuid() throws Exception {
        UUID userUuid = UUID.randomUUID();
        AuthPrincipal principal = new AuthPrincipal(userUuid, "USER");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        Object result =
                resolver.resolveArgument(
                        methodParameter("currentUserUuid", UUID.class), null, null, null);

        assertThat(result).isEqualTo(userUuid);
    }

    @Test
    @DisplayName("Should reject missing authentication")
    void resolveArgument_withoutAuthentication_shouldThrowUnauthorized() throws Exception {
        assertThatThrownBy(
                        () ->
                                resolver.resolveArgument(
                                        methodParameter("currentUserUuid", UUID.class),
                                        null,
                                        null,
                                        null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Should reject invalid principal")
    void resolveArgument_withInvalidPrincipal_shouldThrowUnauthorized() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "not-a-principal", null, List.of()));

        assertThatThrownBy(
                        () ->
                                resolver.resolveArgument(
                                        methodParameter("currentUserUuid", UUID.class),
                                        null,
                                        null,
                                        null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @SuppressWarnings("unused")
    private void currentUserUuid(@CurrentUser UUID userUuid) {}

    @SuppressWarnings("unused")
    private void plainUuid(UUID userUuid) {}

    @SuppressWarnings("unused")
    private void currentUserString(@CurrentUser String userUuid) {}

    private MethodParameter methodParameter(String methodName, Class<?> parameterType)
            throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }
}
