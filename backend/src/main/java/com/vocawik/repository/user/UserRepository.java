package com.vocawik.repository.user;

import com.vocawik.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link User} persistence access. */
public interface UserRepository extends JpaRepository<User, Long> {}
