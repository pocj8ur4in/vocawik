package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Resource} persistence access. */
public interface ResourceRepository
        extends JpaRepository<Resource, Long>, ResourceSearchRepository {}
