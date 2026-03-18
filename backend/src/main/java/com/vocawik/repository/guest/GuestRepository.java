package com.vocawik.repository.guest;

import com.vocawik.domain.guest.Guest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Guest} persistence access. */
public interface GuestRepository extends JpaRepository<Guest, Long> {

    Optional<Guest> findByIpAndIsDeletedFalse(String ip);

    Optional<Guest> findByUuidAndIsDeletedFalse(UUID uuid);
}
