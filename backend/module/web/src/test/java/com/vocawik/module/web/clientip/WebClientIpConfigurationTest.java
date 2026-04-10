package com.vocawik.module.web.clientip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebClientIpConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(WebClientIpConfiguration.class, ClientIpResolver.class);

    @Test
    @DisplayName("Should bind client IP properties")
    void clientIpProperties_shouldBind() {
        contextRunner
                .withPropertyValues("web.client-ip.trusted-proxy-cidrs=127.0.0.1/32")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(WebClientIpProperties.class);
                            assertThat(context).hasSingleBean(ClientIpResolver.class);
                            assertThat(
                                            context.getBean(WebClientIpProperties.class)
                                                    .trustedProxyCidrs())
                                    .isEqualTo("127.0.0.1/32");
                        });
    }
}
