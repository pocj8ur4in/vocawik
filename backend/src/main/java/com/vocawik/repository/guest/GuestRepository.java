package com.vocawik.repository.guest;

import com.vocawik.domain.guest.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link Guest} persistence access. */
public interface GuestRepository extends JpaRepository<Guest, Long> {}
