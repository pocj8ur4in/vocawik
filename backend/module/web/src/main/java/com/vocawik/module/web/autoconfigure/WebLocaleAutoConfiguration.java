package com.vocawik.module.web.autoconfigure;

import com.vocawik.module.web.locale.AcceptLanguageRequestLocaleResolver;
import com.vocawik.module.web.locale.RequestLocaleFilter;
import com.vocawik.module.web.locale.RequestLocaleResolver;
import com.vocawik.module.web.locale.WebLocaleProperties;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Automatically configures request locale resolution and servlet locale propagation. */
@AutoConfiguration(after = WebRequestAutoConfiguration.class)
@ConditionalOnClass({Filter.class, WebMvcConfigurer.class})
@EnableConfigurationProperties(WebLocaleProperties.class)
public class WebLocaleAutoConfiguration {

    /** Creates the request locale automatic configuration. */
    public WebLocaleAutoConfiguration() {}

    /**
     * Creates the default request locale resolver backed by the Accept Language header.
     *
     * @param properties locale resolution properties
     * @return request locale resolver
     */
    @Bean
    @ConditionalOnMissingBean(RequestLocaleResolver.class)
    RequestLocaleResolver requestLocaleResolver(WebLocaleProperties properties) {
        return new AcceptLanguageRequestLocaleResolver(
                properties.defaultLocale(), properties.supported());
    }

    /**
     * Creates the request locale filter used for HTTP locale context propagation.
     *
     * @param requestLocaleResolver request locale resolver
     * @return request locale filter
     */
    @Bean
    @ConditionalOnMissingBean(RequestLocaleFilter.class)
    RequestLocaleFilter requestLocaleFilter(RequestLocaleResolver requestLocaleResolver) {
        return new RequestLocaleFilter(requestLocaleResolver);
    }

    /**
     * Registers the request locale filter near the beginning of the servlet filter chain.
     *
     * @param requestLocaleFilter request locale filter
     * @return filter registration bean
     */
    @Bean
    FilterRegistrationBean<RequestLocaleFilter> requestLocaleFilterRegistration(
            RequestLocaleFilter requestLocaleFilter) {
        FilterRegistrationBean<RequestLocaleFilter> registration =
                new FilterRegistrationBean<>(requestLocaleFilter);
        registration.setName("requestLocaleFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
