package com.vocawik.module.security.user;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers current-user argument resolution for Spring MVC controllers. */
@Configuration(proxyBeanMethods = false)
public class CurrentUserConfiguration implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    /**
     * Creates a configuration with the current-user argument resolver.
     *
     * @param currentUserArgumentResolver resolver for {@link CurrentUser} parameters
     */
    public CurrentUserConfiguration(CurrentUserArgumentResolver currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    /**
     * Adds the current-user argument resolver to Spring MVC.
     *
     * @param resolvers argument resolver list
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
