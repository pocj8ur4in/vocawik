package com.vocawik.module.web.autoconfigure;

import com.vocawik.module.web.cors.WebCorsProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/** Automatically configures the shared servlet CORS policy. */
@AutoConfiguration
@ConditionalOnClass({HttpServletRequest.class, CorsConfigurationSource.class})
@EnableConfigurationProperties(WebCorsProperties.class)
public class WebCorsAutoConfiguration {

    /** Creates the CORS automatic configuration. */
    public WebCorsAutoConfiguration() {}

    /**
     * Creates the CORS configuration.
     *
     * @param properties CORS policy properties
     * @return CORS configuration source
     */
    @Bean
    @ConditionalOnMissingBean(
            value = CorsConfigurationSource.class,
            ignored = HandlerMappingIntrospector.class)
    CorsConfigurationSource corsConfigurationSource(WebCorsProperties properties) {
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
