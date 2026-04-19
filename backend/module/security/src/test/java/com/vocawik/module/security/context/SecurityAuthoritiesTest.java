package com.vocawik.module.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecurityAuthoritiesTest {

    @Test
    @DisplayName("Should create role authority")
    void role_withRoleName_shouldReturnRoleAuthority() {
        assertThat(SecurityAuthorities.role("ADMIN")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should keep role authority")
    void role_withRoleAuthority_shouldReturnSameValue() {
        assertThat(SecurityAuthorities.role("ROLE_ADMIN")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should trim role")
    void role_withWhitespace_shouldTrimRole() {
        assertThat(SecurityAuthorities.role(" ADMIN ")).isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should reject blank role")
    void role_withBlankRole_shouldThrowException() {
        assertThatThrownBy(() -> SecurityAuthorities.role(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("role is required.");
    }
}
