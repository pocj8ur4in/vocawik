package com.vocawik.config;

import com.vocawik.security.CurrentUser;
import com.vocawik.security.CurrentUserArgumentResolver;
import com.vocawik.security.guest.CurrentGuestArgumentResolver;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 *
 * <p>Adds {@code /api/v1} path prefix and registers {@link CurrentUserArgumentResolver} for {@link
 * CurrentUser}.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final CurrentGuestArgumentResolver currentGuestArgumentResolver;

    /** For keeping Bean Validation messages in English regardless of request locale. */
    @Bean
    public LocalValidatorFactoryBean validator() {
        MessageInterpolator defaultInterpolator =
                Validation.byDefaultProvider().configure().getDefaultMessageInterpolator();
        MessageInterpolator englishInterpolator =
                new MessageInterpolator() {
                    @Override
                    public String interpolate(String messageTemplate, Context context) {
                        return defaultInterpolator.interpolate(
                                messageTemplate, context, Locale.ENGLISH);
                    }

                    @Override
                    public String interpolate(
                            String messageTemplate, Context context, Locale locale) {
                        return defaultInterpolator.interpolate(
                                messageTemplate, context, Locale.ENGLISH);
                    }
                };

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(englishInterpolator);
        return validator;
    }

    /**
     * Registers {@code /api/v1} prefix for controllers in the {@code com.vocawik.controller}.
     *
     * @param configurer the path match configurer
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api/v1", HandlerTypePredicate.forBasePackage("com.vocawik.controller"));
    }

    /**
     * Registers {@link CurrentUserArgumentResolver} to resolve {@link CurrentUser} parameters.
     *
     * @param resolvers the list of argument resolvers
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
        resolvers.add(currentGuestArgumentResolver);
    }

    /** Returns validator above for requests. */
    @Override
    public Validator getValidator() {
        return validator();
    }
}
