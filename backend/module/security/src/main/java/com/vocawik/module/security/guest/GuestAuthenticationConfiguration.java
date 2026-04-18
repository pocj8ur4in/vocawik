package com.vocawik.module.security.guest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Configures guest authentication when a guest provider is available. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({GuestAuthenticationProvider.class, RequestMappingHandlerMapping.class})
public class GuestAuthenticationConfiguration {

    /**
     * Creates the filter that authenticates allowed anonymous requests as guests.
     *
     * @param requestMappingHandlerMapping Spring MVC handler mapping
     * @param guestAuthenticationProvider provider for guest principals
     * @return guest authentication filter
     */
    @Bean
    public GuestAuthenticationFilter guestAuthenticationFilter(
            RequestMappingHandlerMapping requestMappingHandlerMapping,
            GuestAuthenticationProvider guestAuthenticationProvider) {
        return new GuestAuthenticationFilter(
                requestMappingHandlerMapping, guestAuthenticationProvider);
    }

    /**
     * Prevents guest authentication from running outside the Spring Security filter chain.
     *
     * @param guestAuthenticationFilter guest authentication filter
     * @return disabled servlet registration for the guest filter
     */
    @Bean
    public FilterRegistrationBean<GuestAuthenticationFilter> guestAuthenticationFilterRegistration(
            GuestAuthenticationFilter guestAuthenticationFilter) {
        FilterRegistrationBean<GuestAuthenticationFilter> registration =
                new FilterRegistrationBean<>(guestAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
