package com.vocawik.module.web.clientip;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configures client IP resolution support. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebClientIpProperties.class)
public class WebClientIpConfiguration {}
