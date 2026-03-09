package com.vocawik.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Jackson configuration used by persistence-side JSON projection assembly. */
@Configuration
public class PersistenceJacksonConfig {

    /** Provides a mapper dedicated to persistence JSON payload assembly. */
    @Bean
    public ObjectMapper persistenceObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
