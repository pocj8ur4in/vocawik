package com.vocawik.module.security.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class DisabledUserDetailsServiceConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(DisabledUserDetailsServiceConfiguration.class);

    @Test
    @DisplayName("Should register disabled user details service")
    void userDetailsService_shouldRejectLocalLogin() {
        contextRunner.run(
                context -> {
                    UserDetailsService userDetailsService =
                            context.getBean(UserDetailsService.class);

                    assertThatThrownBy(() -> userDetailsService.loadUserByUsername("user"))
                            .isInstanceOf(UsernameNotFoundException.class)
                            .hasMessage("Local username/password login is disabled.");
                });
    }

    @Test
    @DisplayName("Should not override custom user details service")
    void userDetailsService_withCustomBean_shouldBackOff() {
        UserDetailsService customUserDetailsService =
                username ->
                        User.withUsername(username)
                                .password("password")
                                .authorities("ROLE_USER")
                                .build();

        contextRunner
                .withBean(UserDetailsService.class, () -> customUserDetailsService)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(UserDetailsService.class);
                            assertThat(context.getBean(UserDetailsService.class))
                                    .isSameAs(customUserDetailsService);
                        });
    }
}
