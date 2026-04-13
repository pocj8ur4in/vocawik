package com.vocawik.module.web.locale;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Configures request locale support. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebLocaleProperties.class)
public class WebLocaleConfiguration {

    /**
     * Creates the request locale filter used for HTTP locale context propagation.
     *
     * @param properties locale resolution properties
     * @return request locale filter
     */
    @Bean
    public RequestLocaleFilter requestLocaleFilter(WebLocaleProperties properties) {
        return new RequestLocaleFilter(properties);
    }

    /**
     * Registers the request locale filter near the beginning of the servlet filter chain.
     *
     * @param requestLocaleFilter request locale filter
     * @return filter registration bean
     */
    @Bean
    public FilterRegistrationBean<RequestLocaleFilter> requestLocaleFilterRegistration(
            RequestLocaleFilter requestLocaleFilter) {
        FilterRegistrationBean<RequestLocaleFilter> registration =
                new FilterRegistrationBean<>(requestLocaleFilter);
        registration.setName("requestLocaleFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
