package com.vocawik.module.web.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/** Automatically configures the module's additive Jackson serialization customizer. */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class WebJacksonAutoConfiguration {

    /** Creates the Jackson automatic configuration. */
    public WebJacksonAutoConfiguration() {}

    /**
     * Customizes Jackson object mapper to serialize dates as ISO strings.
     *
     * @return Jackson builder customizer
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer webJacksonCustomizer() {
        return builder -> builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
