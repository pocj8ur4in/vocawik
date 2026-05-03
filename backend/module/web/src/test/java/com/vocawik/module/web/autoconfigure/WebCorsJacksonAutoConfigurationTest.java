package com.vocawik.module.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.vocawik.module.web.cors.WebCorsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class WebCorsJacksonAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    WebCorsAutoConfiguration.class,
                                    WebJacksonAutoConfiguration.class));

    @Test
    @DisplayName("Should discover CORS and Jackson features automatically")
    void autoConfiguration_withServletAndJacksonClasspath_shouldRegisterFeatures() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(WebCorsProperties.class);
                    assertThat(context).hasSingleBean(CorsConfigurationSource.class);
                    assertThat(context)
                            .hasSingleBean(Jackson2ObjectMapperBuilderCustomizer.class);
                });
    }

    @Test
    @DisplayName("Should bind CORS properties")
    void corsAutoConfiguration_withProperties_shouldConfigureSource() {
        contextRunner
                .withPropertyValues(
                        "web.cors.allowed-origins=https://vocawik.com",
                        "web.cors.allowed-methods=GET,POST",
                        "web.cors.allowed-headers=Authorization,Content-Type",
                        "web.cors.exposed-headers=X-Request-Id",
                        "web.cors.allow-credentials=false",
                        "web.cors.max-age=600",
                        "web.cors.path-pattern=/api/**")
                .run(
                        context -> {
                            CorsConfiguration configuration =
                                    corsConfiguration(
                                            context.getBean(CorsConfigurationSource.class),
                                            "/api/status");
                            assertThat(configuration.getAllowedOrigins())
                                    .containsExactly("https://vocawik.com");
                            assertThat(configuration.getAllowedMethods())
                                    .containsExactly("GET", "POST");
                            assertThat(configuration.getAllowedHeaders())
                                    .containsExactly("Authorization", "Content-Type");
                            assertThat(configuration.getExposedHeaders())
                                    .containsExactly("X-Request-Id");
                            assertThat(configuration.getAllowCredentials()).isFalse();
                            assertThat(configuration.getMaxAge()).isEqualTo(600L);
                        });
    }

    @Test
    @DisplayName("Should reject wildcard CORS origins with credentials")
    void corsAutoConfiguration_withWildcardOriginsAndCredentials_shouldFail() {
        contextRunner
                .withPropertyValues(
                        "web.cors.allowed-origins=*", "web.cors.allow-credentials=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should preserve a consumer CORS source")
    void corsAutoConfiguration_withConsumerSource_shouldBackOff() {
        CorsConfigurationSource consumerSource = request -> null;

        contextRunner
                .withBean(CorsConfigurationSource.class, () -> consumerSource)
                .run(
                        context ->
                                assertThat(context.getBean(CorsConfigurationSource.class))
                                        .isSameAs(consumerSource));
    }

    @Test
    @DisplayName("Should serialize dates as ISO strings")
    void jacksonAutoConfiguration_shouldDisableDateTimestamps() {
        contextRunner.run(
                context -> {
                    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
                    context.getBean(Jackson2ObjectMapperBuilderCustomizer.class).customize(builder);

                    ObjectMapper objectMapper = builder.build();

                    assertThat(
                                    objectMapper.isEnabled(
                                            SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                            .isFalse();
                });
    }

    private CorsConfiguration corsConfiguration(CorsConfigurationSource source, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertThat(configuration).isNotNull();
        return configuration;
    }
}
