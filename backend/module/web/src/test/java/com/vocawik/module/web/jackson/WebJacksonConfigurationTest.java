package com.vocawik.module.web.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class WebJacksonConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(WebJacksonConfiguration.class);

    @Test
    @DisplayName("Should register Jackson customizer")
    void webJacksonCustomizer_shouldRegister() {
        contextRunner.run(
                context ->
                        assertThat(context)
                                .hasSingleBean(Jackson2ObjectMapperBuilderCustomizer.class));
    }

    @Test
    @DisplayName("Should serialize dates as ISO strings")
    void webJacksonCustomizer_shouldDisableDateTimestamps() {
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
}
