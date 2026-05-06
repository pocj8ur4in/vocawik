package com.vocawik.module.security.autoconfigure;

import com.vocawik.module.security.guest.CurrentGuestArgumentResolver;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Automatically configures MVC argument resolution for current guest principals. */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
public class SecurityCurrentGuestAutoConfiguration implements WebMvcConfigurer {

    private final ObjectProvider<CurrentGuestArgumentResolver> currentGuestArgumentResolver;

    /**
     * Creates the current guest MVC configuration.
     *
     * @param currentGuestArgumentResolver current guest resolver provider
     */
    public SecurityCurrentGuestAutoConfiguration(
            ObjectProvider<CurrentGuestArgumentResolver> currentGuestArgumentResolver) {
        this.currentGuestArgumentResolver = currentGuestArgumentResolver;
    }

    /**
     * Creates the default current guest argument resolver.
     *
     * @return current guest argument resolver
     */
    @Bean
    @ConditionalOnMissingBean(CurrentGuestArgumentResolver.class)
    CurrentGuestArgumentResolver currentGuestArgumentResolver() {
        return new CurrentGuestArgumentResolver();
    }

    /**
     * Adds the current guest resolver to Spring MVC.
     *
    * @param resolvers MVC argument resolver list
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        currentGuestArgumentResolver.ifAvailable(resolvers::add);
    }
}
