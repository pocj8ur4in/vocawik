package com.vocawik.module.web.clientip;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties for client IP resolution.
 *
 * @param trustedProxyCidrs comma-separated trusted proxy CIDR ranges
 */
@ConfigurationProperties(prefix = "web.client-ip")
public record WebClientIpProperties(@DefaultValue("") String trustedProxyCidrs) {}
