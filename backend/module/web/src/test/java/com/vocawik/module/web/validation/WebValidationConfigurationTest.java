package com.vocawik.module.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

class WebValidationConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(WebValidationConfiguration.class);

    @Test
    @DisplayName("Should register web validator and MVC configurer")
    void webValidator_shouldRegisterBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(LocalValidatorFactoryBean.class);
                    assertThat(context).hasSingleBean(WebMvcConfigurer.class);
                });
    }

    @Test
    @DisplayName("Should interpolate validation messages in English")
    void webValidator_withKoreanLocale_shouldUseEnglishMessage() {
        Locale previousLocale = LocaleContextHolder.getLocale();
        LocaleContextHolder.setLocale(Locale.KOREAN);
        try {
            contextRunner.run(
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

    private record SampleRequest(@NotBlank String name) {}
}
