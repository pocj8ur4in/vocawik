package com.vocawik.module.security.autoconfigure;

import com.vocawik.module.security.jwt.JwtAuthenticationFilter;
import com.vocawik.module.security.jwt.JwtProperties;
import com.vocawik.module.security.jwt.JwtProvider;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/** Automatically configures JWT authentication when a signing-secret property is present. */
@AutoConfiguration
@ConditionalOnClass({Jwts.class, Filter.class})
@ConditionalOnProperty(prefix = "security.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityJwtAutoConfiguration {

    /** Creates the JWT automatic configuration. */
    public SecurityJwtAutoConfiguration() {}

    /**
     * Creates the JWT provider used to issue and verify tokens.
     *
     * @param properties JWT configuration properties
     * @return JWT provider
     */
    @Bean
    @ConditionalOnMissingBean(JwtProvider.class)
    JwtProvider jwtProvider(JwtProperties properties) {
        return new JwtProvider(properties);
    }

    /**
     * Creates the filter that authenticates bearer access tokens.
     *
     * @param jwtProvider JWT token provider
     * @return JWT authentication filter
     */
    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtProvider jwtProvider) {
        return new JwtAuthenticationFilter(jwtProvider);
    }

    /**
     * Keeps bearer token processing inside the Spring Security filter chain.
     *
     * @param jwtAuthenticationFilter JWT authentication filter
     * @return disabled servlet registration for the JWT filter
     */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
