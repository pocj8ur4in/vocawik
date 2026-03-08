package com.vocawik.security;

import com.vocawik.security.guest.GuestAuthenticationFilter;
import com.vocawik.security.ip.ClientIpHeaderFilter;
import com.vocawik.security.jwt.JwtFilter;
import java.util.List;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>CSRF disabled (stateless REST API)
 *   <li>CORS enabled for frontend development
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

    private static final List<String> ALLOWED_ORIGINS =
            List.of("http://localhost:5173", "http://localhost:3000");

    private final JwtFilter jwtFilter;
    private final GuestAuthenticationFilter guestAuthenticationFilter;
    private final RequestLocaleFilter requestLocaleFilter;
    private final ClientIpHeaderFilter clientIpHeaderFilter;
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
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                                                "/api/v1/registration-verification-requests",
                                                "/api/v1/registration-verifications",
                                                "/api/v1/registrations",
                                                "/api/v1/sessions/**",
                                                "/api/v1/oauth/**",
                                                "/api/v1/status",
                                                "/swagger-ui/**",
                                                "/v3/api-docs/**",
                                                "/actuator/health",
                                                "/actuator/info",
                                                "/api/v1/resources",
                                                "/api/v1/songs",
                                                "/api/v1/artists",
                                                "/api/v1/vocals")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(
                        guestAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(requestLocaleFilter, JwtFilter.class)
                .addFilterAfter(clientIpHeaderFilter, RequestLocaleFilter.class);

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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Client-IP"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
