package com.vocawik.module.web.jackson;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures Jackson serialization behavior. */
@Configuration(proxyBeanMethods = false)
public class WebJacksonConfiguration {

    /**
     * Customizes jackson object mapper to serialize dates as ISO strings.
     *
     * @return Jackson builder customizer
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer webJacksonCustomizer() {
        return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
