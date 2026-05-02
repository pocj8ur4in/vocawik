package com.vocawik.module.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.web.locale.RequestLocaleFilter;
import com.vocawik.module.web.locale.RequestLocaleResolver;
import com.vocawik.module.web.locale.WebLocaleProperties;
import com.vocawik.module.web.request.RequestIdFilter;
import jakarta.servlet.Filter;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class WebRequestLocaleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    WebRequestAutoConfiguration.class,
                                    WebLocaleAutoConfiguration.class));

    @Test
    @DisplayName("Should discover request and locale features automatically")
    void autoConfiguration_withServletClasspath_shouldRegisterFilters() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RequestIdFilter.class);
                    assertThat(context).hasSingleBean(RequestLocaleResolver.class);
                    assertThat(context).hasSingleBean(RequestLocaleFilter.class);
                    assertThat(
                                    context.getBean(
                                            "requestIdFilterRegistration", FilterRegistrationBean.class))
                            .extracting(FilterRegistrationBean::getOrder)
                            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
                    assertThat(
                                    context.getBean(
                                            "requestLocaleFilterRegistration",
                                            FilterRegistrationBean.class))
                            .extracting(FilterRegistrationBean::getOrder)
                            .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
                });
    }

    @Test
    @DisplayName("Should bind locale properties")
    void localeAutoConfiguration_withProperties_shouldBind() {
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

    @Test
    @DisplayName("Should preserve consumer owned request and locale components")
    void autoConfiguration_withConsumerBeans_shouldBackOffReplaceableBeans() {
        RequestIdFilter consumerRequestIdFilter = new RequestIdFilter();
        RequestLocaleResolver consumerLocaleResolver = request -> Locale.JAPANESE;

        contextRunner
                .withBean(RequestIdFilter.class, () -> consumerRequestIdFilter)
                .withBean(RequestLocaleResolver.class, () -> consumerLocaleResolver)
                .run(
                        context -> {
                            assertThat(context.getBean(RequestIdFilter.class))
                                    .isSameAs(consumerRequestIdFilter);
                            assertThat(context.getBean(RequestLocaleResolver.class))
                                    .isSameAs(consumerLocaleResolver);
                            assertThat(context).hasSingleBean(RequestLocaleFilter.class);
                        });
    }

    @Test
    @DisplayName("Should skip request and locale features without servlet support")
    void autoConfiguration_withoutServletClasspath_shouldRemainInactive() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Filter.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(RequestIdFilter.class);
                            assertThat(context).doesNotHaveBean(RequestLocaleResolver.class);
                            assertThat(context).doesNotHaveBean(RequestLocaleFilter.class);
                        });
    }
}
