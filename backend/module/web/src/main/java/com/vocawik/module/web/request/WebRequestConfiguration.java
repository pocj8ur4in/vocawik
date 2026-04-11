package com.vocawik.module.web.request;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Configures request correlation support. */
@Configuration(proxyBeanMethods = false)
public class WebRequestConfiguration {

    /**
     * Creates the request ID filter used for HTTP request correlation.
     *
     * @return request ID filter
     */
    @Bean
    public RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    /**
     * Registers the request ID filter before security and application filters.
     *
     * @param requestIdFilter request ID filter
     * @return filter registration bean
     */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
            RequestIdFilter requestIdFilter) {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(requestIdFilter);
        registration.setName("requestIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
