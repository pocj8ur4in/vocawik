package com.vocawik.module.persistence.autoconfigure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Automatically configures Querydsl when a consumer exposes a JPA entity manager. */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({JPAQueryFactory.class, EntityManager.class})
@ConditionalOnBean(EntityManager.class)
public class PersistenceQuerydslAutoConfiguration {

    /** Creates the Querydsl automatic configuration. */
    public PersistenceQuerydslAutoConfiguration() {}

    /**
     * Creates the default Querydsl JPA query factory.
     *
     * @param entityManager JPA entity manager
     * @return Querydsl query factory
     */
    @Bean
    @ConditionalOnMissingBean
    JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
