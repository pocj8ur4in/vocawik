package com.vocawik.module.security.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class CurrentGuestArgumentResolverTest {

    private final CurrentGuestArgumentResolver resolver = new CurrentGuestArgumentResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should support CurrentGuest UUID parameter")
    void supportsParameter_withCurrentGuestUuid_shouldReturnTrue() throws Exception {
        assertThat(resolver.supportsParameter(methodParameter("currentGuestUuid", UUID.class)))
                .isTrue();
    }

    @Test
    @DisplayName("Should not support parameter without CurrentGuest")
    void supportsParameter_withoutCurrentGuest_shouldReturnFalse() throws Exception {
        assertThat(resolver.supportsParameter(methodParameter("plainUuid", UUID.class))).isFalse();
    }

    @Test
    @DisplayName("Should not support CurrentGuest non-UUID parameter")
    void supportsParameter_withCurrentGuestString_shouldReturnFalse() throws Exception {
        assertThat(resolver.supportsParameter(methodParameter("currentGuestString", String.class)))
                .isFalse();
    }

    @Test
    @DisplayName("Should resolve UUID from GuestPrincipal")
    void resolveArgument_withGuestPrincipal_shouldReturnGuestUuid() throws Exception {
        UUID guestUuid = UUID.randomUUID();
        GuestPrincipal principal = new GuestPrincipal(guestUuid);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        Object result =
                resolver.resolveArgument(
                        methodParameter("currentGuestUuid", UUID.class), null, null, null);

        assertThat(result).isEqualTo(guestUuid);
    }

    @Test
    @DisplayName("Should reject missing authentication")
    void resolveArgument_withoutAuthentication_shouldThrowUnauthorized() throws Exception {
        assertThatThrownBy(
                        () ->
                                resolver.resolveArgument(
                                        methodParameter("currentGuestUuid", UUID.class),
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
                                        methodParameter("currentGuestUuid", UUID.class),
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
    private void currentGuestUuid(@CurrentGuest UUID guestUuid) {}

    @SuppressWarnings("unused")
    private void plainUuid(UUID guestUuid) {}

    @SuppressWarnings("unused")
    private void currentGuestString(@CurrentGuest String guestUuid) {}

    private MethodParameter methodParameter(String methodName, Class<?> parameterType)
            throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod(methodName, parameterType);
        return new MethodParameter(method, 0);
    }
}
