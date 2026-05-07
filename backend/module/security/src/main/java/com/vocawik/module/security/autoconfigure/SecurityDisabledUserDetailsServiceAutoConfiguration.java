package com.vocawik.module.security.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** Automatically configures a fallback that disables local username/password user lookup. */
@AutoConfiguration
@ConditionalOnClass(UserDetailsService.class)
public class SecurityDisabledUserDetailsServiceAutoConfiguration {

    /** Creates the disabled user details service automatic configuration. */
    public SecurityDisabledUserDetailsServiceAutoConfiguration() {}

    /**
     * Creates a user details service that rejects local username and password authentication.
     *
     * @return fallback user details service
     */
    @Bean
    @ConditionalOnMissingBean(UserDetailsService.class)
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Local username/password login is disabled.");
        };
    }
}
