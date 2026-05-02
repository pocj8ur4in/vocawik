package com.vocawik.module.web.autoconfigure;

import com.vocawik.module.web.request.RequestIdFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** Automatically configures servlet request correlation and response request IDs. */
@AutoConfiguration
@ConditionalOnClass(Filter.class)
public class WebRequestAutoConfiguration {

    /** Creates the request correlation automatic configuration. */
    public WebRequestAutoConfiguration() {}

    /**
     * Creates the request ID filter used for HTTP request correlation.
     *
     * @return request ID filter
     */
    @Bean
    @ConditionalOnMissingBean(RequestIdFilter.class)
    RequestIdFilter requestIdFilter() {
        return new RequestIdFilter();
    }

    /**
     * Registers the request ID filter before security and application filters.
     *
     * @param requestIdFilter request ID filter
     * @return filter registration bean
     */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
            RequestIdFilter requestIdFilter) {
        FilterRegistrationBean<RequestIdFilter> registration =
                new FilterRegistrationBean<>(requestIdFilter);
        registration.setName("requestIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
