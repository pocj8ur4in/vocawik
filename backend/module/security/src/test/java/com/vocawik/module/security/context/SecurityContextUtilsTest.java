package com.vocawik.module.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.security.guest.GuestPrincipal;
import com.vocawik.module.security.jwt.AuthPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class SecurityContextUtilsTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should return empty authentication when none exists")
    void currentAuthentication_withoutAuthentication_shouldReturnEmpty() {
        assertThat(SecurityContextUtils.currentAuthentication()).isEmpty();
    }

    @Test
    @DisplayName("Should return current authentication")
    void currentAuthentication_withAuthentication_shouldReturnAuthentication() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("principal", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThat(SecurityContextUtils.currentAuthentication()).contains(authentication);
    }

    @Test
    @DisplayName("Should return current user principal")
    void currentUser_withAuthPrincipal_shouldReturnPrincipal() {
        AuthPrincipal principal = new AuthPrincipal(UUID.randomUUID(), "ADMIN");
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(SecurityContextUtils.currentUser()).contains(principal);
        assertThat(SecurityContextUtils.currentGuest()).isEmpty();
    }

    @Test
    @DisplayName("Should return current guest principal")
    void currentGuest_withGuestPrincipal_shouldReturnPrincipal() {
        GuestPrincipal principal = new GuestPrincipal(UUID.randomUUID());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(SecurityContextUtils.currentGuest()).contains(principal);
        assertThat(SecurityContextUtils.currentUser()).isEmpty();
    }

    @Test
    @DisplayName("Should check current authority")
    void hasAuthority_withMatchingAuthority_shouldReturnTrue() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "principal",
                                null,
                                List.of(new SimpleGrantedAuthority("resource:write"))));

        assertThat(SecurityContextUtils.hasAuthority("resource:write")).isTrue();
        assertThat(SecurityContextUtils.hasAuthority("resource:read")).isFalse();
    }

    @Test
    @DisplayName("Should check current role")
    void hasRole_withMatchingRole_shouldReturnTrue() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "principal",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        assertThat(SecurityContextUtils.hasRole("ADMIN")).isTrue();
        assertThat(SecurityContextUtils.hasRole("ROLE_ADMIN")).isTrue();
        assertThat(SecurityContextUtils.hasRole("USER")).isFalse();
    }
}
