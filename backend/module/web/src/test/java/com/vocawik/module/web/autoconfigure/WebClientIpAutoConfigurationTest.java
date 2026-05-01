package com.vocawik.module.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.web.clientip.ClientIpResolver;
import com.vocawik.module.web.clientip.WebClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebClientIpAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(WebClientIpAutoConfiguration.class));

    @Test
    @DisplayName("Should discover client IP resolution automatically")
    void autoConfiguration_withServletClasspath_shouldRegisterResolver() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(WebClientIpProperties.class);
                    assertThat(context).hasSingleBean(ClientIpResolver.class);
                });
    }

    @Test
    @DisplayName("Should bind client IP properties")
    void autoConfiguration_withTrustedProxyProperties_shouldBind() {
        contextRunner
                .withPropertyValues("web.client-ip.trusted-proxy-cidrs=127.0.0.1/32")
                .run(
                        context ->
                                assertThat(
                                                context.getBean(WebClientIpProperties.class)
                                                        .trustedProxyCidrs())
                                        .isEqualTo("127.0.0.1/32"));
    }

    @Test
    @DisplayName("Should preserve a consumer client IP resolver")
    void autoConfiguration_withConsumerResolver_shouldBackOff() {
        ClientIpResolver consumerResolver =
                new ClientIpResolver(new WebClientIpProperties("127.0.0.1/32"));

        contextRunner
                .withBean(ClientIpResolver.class, () -> consumerResolver)
                .run(
                        context ->
                                assertThat(context.getBean(ClientIpResolver.class))
                                        .isSameAs(consumerResolver));
    }

    @Test
    @DisplayName("Should skip client IP resolution without servlet support")
    void autoConfiguration_withoutServletClasspath_shouldRemainInactive() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(HttpServletRequest.class))
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(WebClientIpProperties.class);
                            assertThat(context).doesNotHaveBean(ClientIpResolver.class);
                        });
    }
}
