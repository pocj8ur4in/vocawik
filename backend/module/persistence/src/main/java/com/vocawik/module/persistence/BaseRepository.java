package com.vocawik.module.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Common repository contract for entities backed by {@link BaseEntity}.
 *
 * @param <T> persistent entity type governed by the shared entity contract
 */
@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity> extends JpaRepository<T, Long> {}
