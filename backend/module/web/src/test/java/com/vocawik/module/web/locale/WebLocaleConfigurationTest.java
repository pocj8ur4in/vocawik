package com.vocawik.module.web.locale;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class WebLocaleConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(WebLocaleConfiguration.class);

    @Test
    @DisplayName("Should register request locale filter with defaults")
    void requestLocaleFilter_shouldRegisterWithDefaults() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(WebLocaleProperties.class);
                    assertThat(context).hasSingleBean(RequestLocaleFilter.class);
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context.getBean(FilterRegistrationBean.class).getOrder())
                            .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);

                    WebLocaleProperties properties = context.getBean(WebLocaleProperties.class);
                    assertThat(properties.defaultLocale()).isEqualTo(Locale.ENGLISH);
                    assertThat(properties.supported())
                            .containsExactly(
                                    Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE, Locale.CHINESE);
                });
    }

    @Test
    @DisplayName("Should bind locale properties")
    void localeProperties_shouldBind() {
        contextRunner
                .withPropertyValues("web.locale.default=ja", "web.locale.supported=ja,en")
                .run(
                        context -> {
                            WebLocaleProperties properties =
                                    context.getBean(WebLocaleProperties.class);

                            assertThat(properties.defaultLocale()).isEqualTo(Locale.JAPANESE);
                            assertThat(properties.supported())
                                    .containsExactly(Locale.JAPANESE, Locale.ENGLISH);
                        });
    }
}
