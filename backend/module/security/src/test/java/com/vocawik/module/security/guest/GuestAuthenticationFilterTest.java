package com.vocawik.module.security.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class GuestAuthenticationFilterTest {

    private final RequestMappingHandlerMapping handlerMapping =
            mock(RequestMappingHandlerMapping.class);
    private final GuestAuthenticationProvider provider = mock(GuestAuthenticationProvider.class);
    private final GuestAuthenticationFilter filter =
            new GuestAuthenticationFilter(handlerMapping, provider);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should authenticate allowed guest request")
    void doFilter_withAllowGuestHandler_shouldSetAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID guestUuid = UUID.randomUUID();
        when(handlerMapping.getHandler(request))
                .thenReturn(new HandlerExecutionChain(handlerMethod("guestEndpoint")));
        when(provider.authenticate(request)).thenReturn(Optional.of(new GuestPrincipal(guestUuid)));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new GuestPrincipal(guestUuid));
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_GUEST");
    }

    @Test
    @DisplayName("Should skip guest authentication when Authorization header exists")
    void doFilter_withAuthorizationHeader_shouldSkipAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token");

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(provider, never()).authenticate(request);
    }

    @Test
    @DisplayName("Should skip guest authentication when handler is not allowed")
    void doFilter_withoutAllowGuestHandler_shouldSkipAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(handlerMapping.getHandler(request))
                .thenReturn(new HandlerExecutionChain(handlerMethod("protectedEndpoint")));

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(provider, never()).authenticate(request);
    }

    @Test
    @DisplayName("Should skip guest authentication when provider returns empty")
    void doFilter_withEmptyProviderResult_shouldSkipAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(handlerMapping.getHandler(request))
                .thenReturn(new HandlerExecutionChain(handlerMethod("guestEndpoint")));
        when(provider.authenticate(request)).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Method method = GuestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new GuestController(), method);
    }

    private static final class GuestController {

        @AllowGuest
        void guestEndpoint() {}

        void protectedEndpoint() {}
    }
}
