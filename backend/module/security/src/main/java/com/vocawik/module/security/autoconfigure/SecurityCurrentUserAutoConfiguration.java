package com.vocawik.module.security.autoconfigure;

import com.vocawik.module.security.user.CurrentUserArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Automatically configures MVC argument resolution for current authenticated users. */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
public class SecurityCurrentUserAutoConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<CurrentUserArgumentResolver> currentUserArgumentResolver;

    /**
     * Creates the current user MVC configuration.
     *
     * @param currentUserArgumentResolver current user resolver provider
     */
    public SecurityCurrentUserAutoConfiguration(
            ObjectProvider<CurrentUserArgumentResolver> currentUserArgumentResolver) {
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    /**
     * Creates the default current user argument resolver.
     *
     * @return current user argument resolver
     */
    @Bean
    @ConditionalOnMissingBean(CurrentUserArgumentResolver.class)
    CurrentUserArgumentResolver currentUserArgumentResolver() {
        return new CurrentUserArgumentResolver();
    }

    /**
     * Adds the current user resolver to Spring MVC.
     *
     * @param resolvers MVC argument resolver list
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        currentUserArgumentResolver.ifAvailable(resolvers::add);
    }
}
