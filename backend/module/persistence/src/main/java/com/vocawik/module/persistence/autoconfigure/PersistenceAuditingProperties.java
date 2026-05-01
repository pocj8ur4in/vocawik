package com.vocawik.module.persistence.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Controls whether module owned JPA auditing is enabled.
 *
 * @param enabled whether the module may provide JPA auditing infrastructure
 */
@ConfigurationProperties(prefix = "persistence.auditing")
public record PersistenceAuditingProperties(@DefaultValue("true") boolean enabled) {}
