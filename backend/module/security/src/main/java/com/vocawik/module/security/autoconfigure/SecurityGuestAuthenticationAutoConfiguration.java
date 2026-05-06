package com.vocawik.module.security.autoconfigure;

import com.vocawik.module.security.guest.GuestAuthenticationFilter;
import com.vocawik.module.security.guest.GuestAuthenticationProvider;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Automatically configures guest authentication when its provider and MVC handler mapping are available. */
@AutoConfiguration(
        afterName = "org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration")
@ConditionalOnClass({Filter.class, RequestMappingHandlerMapping.class})
@ConditionalOnBean({GuestAuthenticationProvider.class, RequestMappingHandlerMapping.class})
public class SecurityGuestAuthenticationAutoConfiguration {

    /** Creates the guest authentication automatic configuration. */
    public SecurityGuestAuthenticationAutoConfiguration() {}

    /**
     * Creates the filter that authenticates allowed anonymous requests as guests.
     *
     * @param requestMappingHandlerMapping Spring MVC handler mapping
     * @param guestAuthenticationProvider provider for guest principals
     * @return guest authentication filter
     */
    @Bean
    @ConditionalOnMissingBean(GuestAuthenticationFilter.class)
    GuestAuthenticationFilter guestAuthenticationFilter(
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
    FilterRegistrationBean<GuestAuthenticationFilter> guestAuthenticationFilterRegistration(
            GuestAuthenticationFilter guestAuthenticationFilter) {
        FilterRegistrationBean<GuestAuthenticationFilter> registration =
                new FilterRegistrationBean<>(guestAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
