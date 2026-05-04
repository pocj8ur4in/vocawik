package com.vocawik.module.security.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.module.security.error.ApiAccessDeniedHandler;
import com.vocawik.module.security.error.ApiAuthenticationEntryPoint;
import com.vocawik.module.security.error.SecurityErrorResponseWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Automatically configures JSON authentication and authorization failure responses. */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
            "com.vocawik.module.web.autoconfigure.WebApiErrorAutoConfiguration"
        })
@ConditionalOnClass({HttpServletResponse.class, ObjectMapper.class, AuthenticationEntryPoint.class})
public class SecurityErrorAutoConfiguration {

    /** Creates the security error automatic configuration. */
    public SecurityErrorAutoConfiguration() {}

    /**
     * Creates the default JSON security error writer.
     *
     * @param objectMapper application JSON mapper
     * @return security error response writer
     */
    @Bean
    @ConditionalOnMissingBean(SecurityErrorResponseWriter.class)
    public SecurityErrorResponseWriter securityErrorResponseWriter(ObjectMapper objectMapper) {
        return new SecurityErrorResponseWriter(objectMapper);
    }

    /**
     * Creates the default authentication entry point.
     *
     * @param errorResponseWriter security error writer
     * @return API authentication entry point
     */
    @Bean
    @ConditionalOnMissingBean(ApiAuthenticationEntryPoint.class)
    public ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(
            SecurityErrorResponseWriter errorResponseWriter) {
        return new ApiAuthenticationEntryPoint(errorResponseWriter);
    }

    /**
     * Creates the default access denied handler.
     *
     * @param errorResponseWriter security error writer
     * @return API access denied handler
     */
    @Bean
    @ConditionalOnMissingBean(ApiAccessDeniedHandler.class)
    public ApiAccessDeniedHandler apiAccessDeniedHandler(
            SecurityErrorResponseWriter errorResponseWriter) {
        return new ApiAccessDeniedHandler(errorResponseWriter);
    }
}
