package com.vocawik.module.web.validation;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configures Bean Validation. */
@Configuration(proxyBeanMethods = false)
public class WebValidationConfiguration {

    /**
     * Creates a validator that always interpolates Bean Validation messages in English.
     *
     * @return validator with English message interpolation
     */
    @Bean
    public LocalValidatorFactoryBean webValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(englishMessageInterpolator());
        return validator;
    }

    /**
     * Registers the web validator with Spring MVC.
     *
     * @param webValidator validator used for request validation
     * @return Spring MVC configurer
     */
    @Bean
    public WebMvcConfigurer webValidatorConfigurer(LocalValidatorFactoryBean webValidator) {
        return new WebMvcConfigurer() {
            @Override
            public Validator getValidator() {
                return webValidator;
            }
        };
    }

    /**
     * Creates an interpolator that ignores request locale and uses English.
     *
     * @return English message interpolator
     */
    private MessageInterpolator englishMessageInterpolator() {
        MessageInterpolator defaultInterpolator =
                Validation.byDefaultProvider().configure().getDefaultMessageInterpolator();
        return new EnglishMessageInterpolator(defaultInterpolator);
    }

    /** Message interpolator that always delegates with {@link Locale#ENGLISH}. */
    private static final class EnglishMessageInterpolator implements MessageInterpolator {

        private final MessageInterpolator delegate;

        /**
         * Creates an interpolator backed by the default validation provider.
         *
         * @param delegate default message interpolator
         */
        private EnglishMessageInterpolator(MessageInterpolator delegate) {
            this.delegate = delegate;
        }

        /**
         * Interpolates a validation message in English.
         *
         * @param messageTemplate validation message template
         * @param context validation interpolation context
         * @return interpolated validation message
         */
        @Override
        public String interpolate(String messageTemplate, Context context) {
            return delegate.interpolate(messageTemplate, context, Locale.ENGLISH);
        }

        /**
         * Interpolates a validation message in English regardless of the requested locale.
         *
         * @param messageTemplate validation message template
         * @param context validation interpolation context
         * @param locale ignored requested locale
         * @return interpolated validation message
         */
        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return delegate.interpolate(messageTemplate, context, Locale.ENGLISH);
        }
    }
}
