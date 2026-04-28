package com.vocawik.module.persistence;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides a shared Querydsl factory when JPA is available. */
@Configuration(proxyBeanMethods = false)
public class QuerydslConfiguration {

    /**
     * Creates the default Querydsl JPA query factory.
     *
     * @param entityManager JPA entity manager
     * @return Querydsl query factory
     */
    @Bean
    @ConditionalOnBean(EntityManager.class)
    @ConditionalOnMissingBean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
