package com.vocawik.module.persistence.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Metamodel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.aspectj.AnnotationBeanConfigurerAspect;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.domain.support.AuditingBeanFactoryPostProcessor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;

class PersistenceModuleAutoConfigurationTest {

    private static final String HANDLER = "jpaAuditingHandler";
    private static final String LISTENER = AuditingEntityListener.class.getName();
    private static final String POST_PROCESSOR = AuditingBeanFactoryPostProcessor.class.getName();
    private static final String ASPECT =
            "org.springframework.context.config.internalBeanConfigurerAspect";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(PersistenceJpaAuditingAutoConfiguration.class));

    @Test
    @DisplayName("Should skip auditing when no entity manager factory is available")
    void auditing_withoutEntityManagerFactory_shouldRemainInactive() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(PersistenceAuditingProperties.class);
                    assertThat(context).doesNotHaveBean(AuditingHandler.class);
                });
    }

    @Test
    @DisplayName("Should provide UTC auditing infrastructure when JPA is available")
    void auditing_withEntityManagerFactory_shouldProvideModuleInfrastructure() {
        contextRunner
                .withBean(EntityManagerFactory.class, PersistenceModuleAutoConfigurationTest::entityManagerFactory)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean("persistenceClock", Clock.class).getZone())
                                    .isEqualTo(ZoneOffset.UTC);
                            assertThat(context).hasSingleBean(DateTimeProvider.class);
                            assertThat(
                                            context.getBean(
                                                            "persistenceDateTimeProvider",
                                                            DateTimeProvider.class)
                                                    .getNow())
                                    .isPresent();
                        });
    }

    @Test
    @DisplayName("Should allow explicit auditing opt out without AspectJ")
    void auditing_whenDisabled_shouldSkipMissingAspectFailure() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(AnnotationBeanConfigurerAspect.class))
                .withPropertyValues("persistence.auditing.enabled=false")
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(
                                            context.getBean(PersistenceAuditingProperties.class)
                                                    .enabled())
                                    .isFalse();
                            assertThat(context).doesNotHaveBean(AuditingHandler.class);
                        });
    }

    @Test
    @DisplayName("Should fail when auditing is enabled without AspectJ")
    void auditing_withoutSpringAspects_shouldFailFast() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(AnnotationBeanConfigurerAspect.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .hasMessageContaining("spring-aspects")
                                        .hasMessageContaining("persistence.auditing.enabled=true"));
    }

    @Test
    @DisplayName("Should reject malformed auditing property values")
    void auditing_withMalformedProperty_shouldFailBinding() {
        contextRunner
                .withPropertyValues("persistence.auditing.enabled=invalid")
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Should reject partial consumer auditing ownership")
    void auditing_withPartialConsumerInfrastructure_shouldFailStartup() {
        contextRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withUserConfiguration(PartialAuditingConfiguration.class)
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .hasMessageContaining("Consumer owned JPA auditing"));
    }

    @Test
    @DisplayName("Should reject a consumer handler with a reserved name but wrong type")
    void auditing_withWrongTypeConsumerHandler_shouldFailStartup() {
        contextRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withUserConfiguration(WrongTypeAuditingConfiguration.class)
                .run(
                        context ->
                                assertThat(context.getStartupFailure())
                                        .hasMessageContaining("jpaAuditingHandler")
                                        .hasMessageContaining("AuditingHandler"));
    }

    @Test
    @DisplayName("Should preserve a complete consumer auditing infrastructure")
    void auditing_withCompleteConsumerInfrastructure_shouldBackOffModule() {
        contextRunner
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withUserConfiguration(CompleteAuditingConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasBean(HANDLER);
                            assertThat(context).doesNotHaveBean("persistenceClock");
                            assertThat(context).doesNotHaveBean("persistenceDateTimeProvider");
                        });
    }

    @Test
    @DisplayName("Should provide Querydsl independently when an entity manager is available")
    void querydsl_withEntityManager_shouldCreateFactory() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(PersistenceQuerydslAutoConfiguration.class))
                .withBean(EntityManager.class, () -> mock(EntityManager.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .hasSingleBean(com.querydsl.jpa.impl.JPAQueryFactory.class);
                        });
    }

    @Test
    @DisplayName("Should back off Querydsl when a consumer factory exists")
    void querydsl_withConsumerFactory_shouldKeepConsumerBean() {
        var consumerFactory = new com.querydsl.jpa.impl.JPAQueryFactory(mock(EntityManager.class));

        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(PersistenceQuerydslAutoConfiguration.class))
                .withBean(EntityManager.class, () -> mock(EntityManager.class))
                .withBean(com.querydsl.jpa.impl.JPAQueryFactory.class, () -> consumerFactory)
                .run(
                        context ->
                                assertThat(
                                                context.getBean(
                                                        com.querydsl.jpa.impl.JPAQueryFactory
                                                                .class))
                                        .isSameAs(consumerFactory));
    }

    @Test
    @DisplayName("Should use the primary entity manager for Querydsl")
    void querydsl_withPrimaryEntityManager_shouldCreateFactory() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(PersistenceQuerydslAutoConfiguration.class))
                .withUserConfiguration(PrimaryEntityManagerConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .hasSingleBean(com.querydsl.jpa.impl.JPAQueryFactory.class);
                        });
    }

    @Test
    @DisplayName("Should reject ambiguous entity managers for Querydsl")
    void querydsl_withAmbiguousEntityManagers_shouldFailStartup() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(PersistenceQuerydslAutoConfiguration.class))
                .withUserConfiguration(AmbiguousEntityManagerConfiguration.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    static class PartialAuditingConfiguration {

        @Bean(name = HANDLER)
        AuditingHandler handler() {
            return mock(AuditingHandler.class);
        }
    }

    private static EntityManagerFactory entityManagerFactory() {
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
        when(entityManagerFactory.getMetamodel()).thenReturn(mock(Metamodel.class));
        return entityManagerFactory;
    }

    @Test
    @DisplayName("Should preserve a consumer owned named auditing provider")
    void auditing_withNamedProvider_shouldBackOffModuleProvider() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(PersistenceJpaAuditingAutoConfiguration.class))
                .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class))
                .withUserConfiguration(
                        NamedProviderConfiguration.class, CompleteAuditingConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean("persistenceDateTimeProvider"))
                                    .isSameAs(NamedProviderConfiguration.PROVIDER);
                            assertThat(context).doesNotHaveBean("persistenceClock");
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CompleteAuditingConfiguration {

        @Bean(name = HANDLER)
        AuditingHandler handler() {
            return mock(AuditingHandler.class);
        }

        @Bean(name = "org.springframework.data.jpa.domain.support.AuditingEntityListener")
        AuditingEntityListener listener() {
            return new AuditingEntityListener();
        }

        @Bean(name = "org.springframework.data.jpa.domain.support.AuditingBeanFactoryPostProcessor")
        AuditingBeanFactoryPostProcessor postProcessor() {
            return new AuditingBeanFactoryPostProcessor();
        }

        @Bean(name = ASPECT)
        AnnotationBeanConfigurerAspect aspect() {
            return mock(AnnotationBeanConfigurerAspect.class);
        }

        @Bean(name = "jpaMappingContext")
        JpaMetamodelMappingContext mappingContext() {
            return new JpaMetamodelMappingContext(Set.of(mock(Metamodel.class)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NamedProviderConfiguration {

        private static final DateTimeProvider PROVIDER =
                () -> java.util.Optional.of(Instant.parse("2026-01-02T03:04:05Z"));

        @Bean(name = "persistenceDateTimeProvider")
        DateTimeProvider provider() {
            return PROVIDER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class WrongTypeAuditingConfiguration {

        @Bean(name = HANDLER)
        Object handler() {
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryEntityManagerConfiguration {

        @Bean
        @Primary
        EntityManager primaryEntityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        EntityManager secondaryEntityManager() {
            return mock(EntityManager.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousEntityManagerConfiguration {

        @Bean
        EntityManager firstEntityManager() {
            return mock(EntityManager.class);
        }

        @Bean
        EntityManager secondEntityManager() {
            return mock(EntityManager.class);
        }
    }
}
