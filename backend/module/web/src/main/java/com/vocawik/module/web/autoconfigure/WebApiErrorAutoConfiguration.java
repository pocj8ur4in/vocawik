package com.vocawik.module.web.autoconfigure;

import com.vocawik.module.web.error.GlobalExceptionHandler;
import com.vocawik.module.web.i18n.ErrorMessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Automatically configures localized API exception handling for servlet applications. */
@AutoConfiguration(after = WebLocaleAutoConfiguration.class)
@ConditionalOnClass({HttpServletRequest.class, RestControllerAdvice.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebApiErrorAutoConfiguration {

    /** Creates the API error automatic configuration. */
    public WebApiErrorAutoConfiguration() {}

    /**
     * Creates the default localized error message resolver.
     *
     * @return error message resolver
     */
    @Bean
    @ConditionalOnMissingBean(ErrorMessageResolver.class)
    public ErrorMessageResolver errorMessageResolver() {
        return new ErrorMessageResolver();
    }

    /**
     * Creates the default global API exception handler.
     *
     * @param errorMessageResolver localized error message resolver
     * @return global exception handler
     */
    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler(
            ErrorMessageResolver errorMessageResolver) {
        return new GlobalExceptionHandler(errorMessageResolver);
    }
}
