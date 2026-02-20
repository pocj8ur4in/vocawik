package com.vocawik.security;

import com.vocawik.security.jwt.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>CSRF disabled (stateless REST API)
 *   <li>Session policy set to STATELESS for token-based authentication
 *   <li>{@link com.vocawik.security.jwt.JwtFilter} registered before {@link
 *       UsernamePasswordAuthenticationFilter}
 *   <li>All endpoints are authenticated except for the public ones
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;
    private final ApiAccessDeniedHandler apiAccessDeniedHandler;

    /**
     * Configures the security filter chain.
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if an error occurs during configuration
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        exception ->
                                exception
                                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                                        .accessDeniedHandler(apiAccessDeniedHandler))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/api/v1/auth/**",
                                                "/api/v1/status",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/actuator/health",
                                                "/actuator/info")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Provides a {@link BCryptPasswordEncoder} as the password encoder.
     *
     * @return the password encoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Prevents creating a default in-memory user.
     *
     * @return a user lookup that always fails
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Local username/password login is disabled");
        };
    }
}
