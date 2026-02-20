package com.vocawik.repository.user;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.domain.user.UserAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link UserAuthProvider} persistence access. */
public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {

    Optional<UserAuthProvider> findByProviderAndProviderUserId(
            AuthProvider provider, String providerUserId);
}
