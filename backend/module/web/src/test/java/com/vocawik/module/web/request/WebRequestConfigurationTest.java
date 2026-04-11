package com.vocawik.module.web.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

class WebRequestConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(WebRequestConfiguration.class);

    @Test
    @DisplayName("Should register request ID filter with highest precedence")
    void requestIdFilter_shouldRegisterWithHighestPrecedence() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(RequestIdFilter.class);
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context.getBean(FilterRegistrationBean.class).getOrder())
                            .isEqualTo(Ordered.HIGHEST_PRECEDENCE);
                });
    }
}
