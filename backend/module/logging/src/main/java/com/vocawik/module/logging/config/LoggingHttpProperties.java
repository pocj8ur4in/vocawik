package com.vocawik.module.logging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties for HTTP logging.
 *
 * @param trustedProxyCidrs comma-separated trusted proxy CIDR ranges
 */
@ConfigurationProperties(prefix = "vocawik.logging.http")
public record LoggingHttpProperties(@DefaultValue("") String trustedProxyCidrs) {}
