package com.vocawik.module.security.guest;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers current-guest argument resolution for Spring MVC controllers. */
@Configuration(proxyBeanMethods = false)
public class CurrentGuestConfiguration implements WebMvcConfigurer {

    private final CurrentGuestArgumentResolver currentGuestArgumentResolver;

    /**
     * Creates a configuration with the current-guest argument resolver.
     *
     * @param currentGuestArgumentResolver resolver for {@link CurrentGuest} parameters
     */
    public CurrentGuestConfiguration(CurrentGuestArgumentResolver currentGuestArgumentResolver) {
        this.currentGuestArgumentResolver = currentGuestArgumentResolver;
    }

    /**
     * Adds the current-guest argument resolver to Spring MVC.
     *
     * @param resolvers argument resolver list
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentGuestArgumentResolver);
    }
}
