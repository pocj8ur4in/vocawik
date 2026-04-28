package com.vocawik.module.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class QuerydslConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    @DisplayName("Should provide a Querydsl factory when JPA is available")
    void jpaQueryFactory_shouldBeCreatedWhenJpaIsAvailable() {
        contextRunner
                .withUserConfiguration(QuerydslConfiguration.class)
                .withBean(EntityManager.class, () -> mock(EntityManager.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(JPAQueryFactory.class);
                        });
    }

    @Test
    @DisplayName("Should not replace an application-provided Querydsl factory")
    void jpaQueryFactory_shouldBackOffWhenApplicationProvidesFactory() {
        contextRunner
                .withUserConfiguration(CustomQuerydslBeans.class, QuerydslConfiguration.class)
                .withBean(EntityManager.class, () -> mock(EntityManager.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(JPAQueryFactory.class);
                            assertThat(context.getBean(JPAQueryFactory.class))
                                    .isSameAs(context.getBean(CustomQuerydslBeans.class).factory());
                        });
    }

    @Test
    @DisplayName("Should not provide a Querydsl factory without JPA")
    void jpaQueryFactory_shouldNotBeCreatedWithoutJpa() {
        contextRunner
                .withUserConfiguration(QuerydslConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(JPAQueryFactory.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomQuerydslBeans {

        private final JPAQueryFactory factory = new JPAQueryFactory(mock(EntityManager.class));

        @Bean
        JPAQueryFactory queryFactory() {
            return factory;
        }

        JPAQueryFactory factory() {
            return factory;
        }
    }
}
