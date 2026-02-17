package com.vocawik.repository.user;

import com.vocawik.domain.user.UserAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link UserAuthProvider} persistence access. */
public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {}
