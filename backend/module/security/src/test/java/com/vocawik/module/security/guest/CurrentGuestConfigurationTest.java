package com.vocawik.module.security.guest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

class CurrentGuestConfigurationTest {

    @Test
    @DisplayName("Should register current guest resolver")
    void addArgumentResolvers_shouldRegisterCurrentGuestResolver() {
        CurrentGuestArgumentResolver resolver = new CurrentGuestArgumentResolver();
        CurrentGuestConfiguration configuration = new CurrentGuestConfiguration(resolver);
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        configuration.addArgumentResolvers(resolvers);

        assertThat(resolvers).containsExactly(resolver);
    }
}
