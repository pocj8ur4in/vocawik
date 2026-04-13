package com.vocawik.module.web.cors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Configures CORS policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebCorsProperties.class)
public class WebCorsConfiguration {

    /**
     * Creates the CORS configuration.
     *
     * @param properties CORS policy properties
     * @return CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(WebCorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        configuration.setExposedHeaders(properties.exposedHeaders());
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(properties.pathPattern(), configuration);
        return source;
    }
}
