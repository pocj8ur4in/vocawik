package com.vocawik.repository.acl;

import com.vocawik.domain.acl.Acl;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Acl} persistence access. */
public interface AclRepository extends JpaRepository<Acl, Long> {}
