package com.vocawik.module.web.autoconfigure;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;
import java.util.Locale;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Automatically configures English Bean Validation messages for Spring MVC. */
@AutoConfiguration(before = WebMvcAutoConfiguration.class)
@ConditionalOnClass({jakarta.validation.Validator.class, WebMvcConfigurer.class})
public class WebValidationAutoConfiguration {

    /** Creates the validation automatic configuration. */
    public WebValidationAutoConfiguration() {}

    /**
     * Creates a validator that always interpolates Bean Validation messages in English.
     *
     * @return validator with English message interpolation
     */
    @Bean
    @ConditionalOnMissingBean(Validator.class)
    LocalValidatorFactoryBean webValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setMessageInterpolator(englishMessageInterpolator());
        return validator;
    }

    /**
     * Registers the selected validator with Spring MVC.
     *
     * @param beanFactory factory used to select the validator exposed to Spring MVC
     * @return Spring MVC configurer
     */
    @Bean
    @ConditionalOnBean(Validator.class)
    @ConditionalOnMissingBean(name = "mvcValidator")
    WebMvcConfigurer webValidatorConfigurer(ListableBeanFactory beanFactory) {
        String validatorName = selectValidatorName(beanFactory);
        Validator validator = beanFactory.getBean(validatorName, Validator.class);
        return new WebMvcConfigurer() {
            /**
             * Supplies the selected application validator to Spring MVC.
             *
             * @return validator selected from the application context
             */
            @Override
            public Validator getValidator() {
                return validator;
            }
        };
    }

    /**
     * Selects the validator owned by this module, the conventional application validator, or the
     * sole remaining validator in that order.
     *
     * @param beanFactory factory containing validator candidates
     * @return bean name of the validator that Spring MVC must use
     * @throws IllegalStateException if no unique validator can be selected
     */
    private String selectValidatorName(ListableBeanFactory beanFactory) {
        String[] validatorNames = beanFactory.getBeanNamesForType(Validator.class, false, false);
        for (String candidate : validatorNames) {
            if (candidate.equals("webValidator")) {
                return candidate;
            }
        }
        for (String candidate : validatorNames) {
            if (candidate.equals("validator")) {
                return candidate;
            }
        }
        if (validatorNames.length == 1) {
            return validatorNames[0];
        }
        throw new IllegalStateException("A unique Spring MVC validator is required");
    }

    /**
     * Creates an interpolator that uses English independently of the request locale.
     *
     * @return message interpolator that enforces English validation messages
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
         * Creates an English enforcing wrapper around the provider interpolator.
         *
         * @param delegate provider interpolator that resolves validation messages
         */
        private EnglishMessageInterpolator(MessageInterpolator delegate) {
            this.delegate = delegate;
        }

        /**
         * Interpolates a validation message using English as the module language contract.
         *
         * @param messageTemplate validation message template
         * @param context interpolation context supplied by the validation provider
         * @return message resolved in English
         */
        @Override
        public String interpolate(String messageTemplate, Context context) {
            return delegate.interpolate(messageTemplate, context, Locale.ENGLISH);
        }

        /**
         * Interpolates a validation message in English and intentionally ignores the caller
         * supplied locale.
         *
         * @param messageTemplate validation message template
         * @param context interpolation context supplied by the validation provider
         * @param locale caller supplied locale that is intentionally ignored
         * @return message resolved in English
         */
        @Override
        public String interpolate(String messageTemplate, Context context, Locale locale) {
            return delegate.interpolate(messageTemplate, context, Locale.ENGLISH);
        }
    }
}
