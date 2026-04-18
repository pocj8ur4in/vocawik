package com.vocawik.module.security.http;

import com.vocawik.module.security.error.ApiAccessDeniedHandler;
import com.vocawik.module.security.error.ApiAuthenticationEntryPoint;
import com.vocawik.module.security.guest.GuestAuthenticationFilter;
import com.vocawik.module.security.jwt.JwtAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** Configures the HTTP security filter chain. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityHttpProperties.class)
public class SecurityHttpConfiguration {

    /**
     * Builds the security filter chain with API error handlers.
     *
     * @param http HTTP security builder
     * @param properties HTTP security properties
     * @param authenticationEntryPoint handler for authentication failures
     * @param accessDeniedHandler handler for authorization failures
     * @param guestAuthenticationFilter guest authentication filter
     * @param jwtAuthenticationFilter JWT authentication filter
     * @return configured security filter chain
     * @throws Exception if Spring Security fails to build the chain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityHttpProperties properties,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<GuestAuthenticationFilter> guestAuthenticationFilter,
            ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilter)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(
                        authorize -> {
                            String[] allows = properties.allows().toArray(String[]::new);
                            if (allows.length > 0) {
                                authorize.requestMatchers(allows).permitAll();
                            }
                            authorize.anyRequest().authenticated();
                        });

        guestAuthenticationFilter.ifAvailable(
                filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));
        jwtAuthenticationFilter.ifAvailable(
                filter -> http.addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class));

        return http.build();
    }
}
