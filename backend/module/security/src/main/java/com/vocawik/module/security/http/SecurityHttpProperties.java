package com.vocawik.module.security.http;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties for the stateless HTTP security policy.
 *
 * @param allows request patterns that are allowed without authentication
 */
@ConfigurationProperties(prefix = "security.http")
public record SecurityHttpProperties(@DefaultValue List<String> allows) {

    /**
     * Normalizes nullable lists to an immutable empty list.
     *
     * @param allows request patterns allowed without authentication
     */
    public SecurityHttpProperties {
        allows = allows == null ? List.of() : List.copyOf(allows);
    }
}
