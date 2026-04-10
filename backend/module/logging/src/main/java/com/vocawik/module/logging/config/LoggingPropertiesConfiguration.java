package com.vocawik.module.logging.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers logging configuration properties. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoggingHttpProperties.class)
@ConditionalOnProperty(prefix = "vocawik.logging.http", name = "enabled", havingValue = "true")
public class LoggingPropertiesConfiguration {}
