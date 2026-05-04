package com.vocawik.module.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.web.error.GlobalExceptionHandler;
import com.vocawik.module.web.i18n.ErrorMessageResolver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class WebValidationApiErrorAutoConfigurationTest {

    private final ApplicationContextRunner validationContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(WebValidationAutoConfiguration.class));

    @Test
    @DisplayName("Should discover validation support automatically")
    void validationAutoConfiguration_shouldRegisterValidatorAndMvcConfigurer() {
        validationContextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(LocalValidatorFactoryBean.class);
                    assertThat(context).hasSingleBean(WebMvcConfigurer.class);
                });
    }

    @Test
    @DisplayName("Should interpolate validation messages in English")
    void validationAutoConfiguration_withKoreanLocale_shouldUseEnglishMessage() {
        Locale previousLocale = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            validationContextRunner.run(
                    context -> {
                        LocalValidatorFactoryBean validator =
                                context.getBean(LocalValidatorFactoryBean.class);
                        Set<ConstraintViolation<SampleRequest>> violations =
                                validator.validate(new SampleRequest(""));

                        assertThat(violations)
                                .singleElement()
                                .extracting(ConstraintViolation::getMessage)
                                .isEqualTo("must not be blank");
                    });
        } finally {
            LocaleContextHolder.setLocale(previousLocale);
        }
    }

    @Test
    @DisplayName("Should preserve a consumer Spring MVC validator")
    void validationAutoConfiguration_withConsumerValidator_shouldBackOff() {
        Validator consumerValidator =
                new Validator() {
                    @Override
                    public boolean supports(Class<?> clazz) {
                        return true;
                    }

                    @Override
                    public void validate(Object target, Errors errors) {}
                };

        validationContextRunner
                .withBean(Validator.class, () -> consumerValidator)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(LocalValidatorFactoryBean.class);
                            assertThat(context.getBean(WebMvcConfigurer.class).getValidator())
                                    .isSameAs(consumerValidator);
                        });
    }

    @Test
    @DisplayName("Should discover localized API error handling in servlet applications")
    void apiErrorAutoConfiguration_withServletApplication_shouldRegisterHandlers() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebApiErrorAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ErrorMessageResolver.class);
                            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                        });
    }

    @Test
    @DisplayName("Should preserve consumer API error beans")
    void apiErrorAutoConfiguration_withConsumerBeans_shouldBackOff() {
        ErrorMessageResolver resolver = new ErrorMessageResolver();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(resolver);

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebApiErrorAutoConfiguration.class))
                .withBean(ErrorMessageResolver.class, () -> resolver)
                .withBean(GlobalExceptionHandler.class, () -> handler)
                .run(
                        context -> {
                            assertThat(context.getBean(ErrorMessageResolver.class))
                                    .isSameAs(resolver);
                            assertThat(context.getBean(GlobalExceptionHandler.class))
                                    .isSameAs(handler);
                        });
    }

    private record SampleRequest(@NotBlank String name) {}
}
