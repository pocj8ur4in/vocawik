package com.vocawik.module.security.jwt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures JWT authentication support. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "security.jwt", name = "secret")
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {

    /**
     * Creates the JWT provider used to issue and verify tokens.
     *
     * @param properties JWT configuration properties
     * @return JWT provider
     */
    @Bean
    public JwtProvider jwtProvider(JwtProperties properties) {
        return new JwtProvider(properties);
    }

    /**
     * Creates the filter that authenticates bearer access tokens.
     *
     * @param jwtProvider JWT token provider
     * @return JWT authentication filter
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtProvider jwtProvider) {
        return new JwtAuthenticationFilter(jwtProvider);
    }

    /**
     * Keeps bearer token processing inside the Spring Security filter chain.
     *
     * @param jwtAuthenticationFilter JWT authentication filter
     * @return disabled servlet registration for the JWT filter
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }
}
