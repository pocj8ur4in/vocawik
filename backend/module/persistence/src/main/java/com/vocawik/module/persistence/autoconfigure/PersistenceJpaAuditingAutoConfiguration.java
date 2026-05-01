package com.vocawik.module.persistence.autoconfigure;

import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Automatically configures JPA auditing when a consumer has an initialized entity manager factory. */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, EnableJpaAuditing.class})
@ConditionalOnBean(EntityManagerFactory.class)
@EnableConfigurationProperties(PersistenceAuditingProperties.class)
public class PersistenceJpaAuditingAutoConfiguration {

    /** Creates the JPA auditing automatic configuration. */
    public PersistenceJpaAuditingAutoConfiguration() {}

    private static final String ASPECT_CLASS_NAME =
            "org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect";

    /** Configuration that imports module owned auditing after prerequisite conditions pass. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "persistence.auditing",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnClass(name = ASPECT_CLASS_NAME)
    @Conditional(ModuleAuditingCondition.class)
    @EnableJpaAuditing(dateTimeProviderRef = "persistenceDateTimeProvider")
    static class ModuleAuditingConfiguration {

        /**
         * Creates the default UTC clock used by persistence auditing.
         *
         * @return UTC system clock
         */
        @Bean
        @ConditionalOnMissingBean(Clock.class)
        Clock persistenceClock() {
            return Clock.systemUTC();
        }

        /**
         * Creates the auditing time provider backed by the application clock.
         *
         * @param clock clock used to obtain the current instant
         * @return auditing time provider
         */
        @Bean
        @ConditionalOnMissingBean(name = "persistenceDateTimeProvider")
        DateTimeProvider persistenceDateTimeProvider(Clock clock) {
            return () -> Optional.of(Instant.now(clock));
        }
    }

    /** Fails explicitly when auditing is enabled without the required AspectJ runtime. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "persistence.auditing",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingClass(ASPECT_CLASS_NAME)
    static class MissingSpringAspectsConfiguration {

        /**
         * Creates a fail fast guard for the missing AspectJ auditing prerequisite.
         *
         * @return bean factory post processor that always fails startup
         */
        @Bean
        static BeanFactoryPostProcessor missingSpringAspectsGuard() {
            return beanFactory -> {
                throw new IllegalStateException(
                        "JPA auditing requires spring-aspects when "
                                + "persistence.auditing.enabled=true");
            };
        }
    }

    /** Validates consumer owned auditing infrastructure and rejects partial ownership. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "persistence.auditing",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class AuditingInfrastructureGuardConfiguration {

        /**
         * Creates the consumer infrastructure validator.
         *
         * @return bean factory post processor that validates auditing ownership
         */
        @Bean
        static BeanFactoryPostProcessor auditingInfrastructureGuard() {
            return PersistenceAuditingInfrastructureValidator::validate;
        }
    }

    /** Selects module ownership only when no consumer auditing marker is registered. */
    static class ModuleAuditingCondition implements Condition {

        /**
         * Determines whether module auditing should be imported.
         *
         * @param context condition context containing the current bean registry
         * @param metadata metadata for the configuration class being evaluated
         * @return {@code true} when no consumer auditing marker exists
         */
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return !PersistenceAuditingInfrastructureValidator.hasConsumerOwnedInfrastructure(
                    context.getRegistry());
        }
    }
}
