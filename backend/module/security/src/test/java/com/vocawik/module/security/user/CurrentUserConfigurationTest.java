package com.vocawik.module.security.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class CurrentUserConfigurationTest {

    @Test
    @DisplayName("Should register current user resolver")
    void addArgumentResolvers_shouldRegisterCurrentUserResolver() {
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();
        CurrentUserConfiguration configuration = new CurrentUserConfiguration(resolver);
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        configuration.addArgumentResolvers(resolvers);

        assertThat(resolvers).containsExactly(resolver);
    }
}
