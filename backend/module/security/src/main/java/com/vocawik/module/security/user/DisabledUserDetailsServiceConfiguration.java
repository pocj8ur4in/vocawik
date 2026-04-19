package com.vocawik.module.security.user;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Configures a fallback user lookup that disables local username/password authentication. */
@Configuration(proxyBeanMethods = false)
public class DisabledUserDetailsServiceConfiguration {

    /**
     * Creates a user details service that rejects local username/password authentication.
     *
     * @return fallback user details service
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Local username/password login is disabled.");
        };
    }
}
