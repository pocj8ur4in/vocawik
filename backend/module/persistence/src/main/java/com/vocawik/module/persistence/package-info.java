/**
 * Common persistence infrastructure.
 *
 * <p>Consumers should depend on the persistence module and use its feature specific Spring Boot
 * automatic configurations. JPA auditing requires a consumer provided {@code spring-aspects} runtime
 * when an {@code EntityManagerFactory} is available and is disabled only by explicitly setting
 * {@code persistence.auditing.enabled=false}.
 */
package com.vocawik.module.persistence;
