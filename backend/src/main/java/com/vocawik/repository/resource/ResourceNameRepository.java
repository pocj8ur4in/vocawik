package com.vocawik.repository.resource;

import com.vocawik.domain.resource.ResourceName;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link ResourceName} persistence access. */
public interface ResourceNameRepository extends JpaRepository<ResourceName, Long> {}
