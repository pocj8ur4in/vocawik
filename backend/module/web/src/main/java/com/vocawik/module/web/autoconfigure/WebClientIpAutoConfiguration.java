package com.vocawik.module.web.autoconfigure;

import com.vocawik.module.web.clientip.ClientIpResolver;
import com.vocawik.module.web.clientip.WebClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Automatically configures trusted proxy aware client IP resolution for servlet applications. */
@AutoConfiguration
@ConditionalOnClass(HttpServletRequest.class)
@EnableConfigurationProperties(WebClientIpProperties.class)
public class WebClientIpAutoConfiguration {

    /** Creates the client IP automatic configuration. */
    public WebClientIpAutoConfiguration() {}

    /**
     * Creates the default trusted proxy aware client IP resolver.
     *
     * @param properties client IP resolution properties
     * @return client IP resolver
     */
    @Bean
    @ConditionalOnMissingBean(ClientIpResolver.class)
    ClientIpResolver clientIpResolver(WebClientIpProperties properties) {
        return new ClientIpResolver(properties);
    }
}
