package com.vocawik.security.guest;

import com.vocawik.domain.guest.Guest;
import com.vocawik.repository.guest.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates or resolves a guest identity from a client IP address. */
@Service
@RequiredArgsConstructor
public class GuestIdentityService {

    private final GuestRepository guestRepository;

    /**
     * Resolves (or creates) a guest identity from a client IP address.
     *
     * @param ip client IP address
     * @return existing or newly created guest
     */
    @Transactional
    public Guest findOrCreateByIp(String ip) {
        Guest guest;
        try {
            guest =
                    guestRepository
                            .findByIpAndIsDeletedFalse(ip)
                            .orElseGet(() -> guestRepository.save(Guest.create(ip)));
        } catch (DataIntegrityViolationException ex) {
            guest = guestRepository.findByIpAndIsDeletedFalse(ip).orElseThrow(() -> ex);
        }
        guest.touchLastSeenAt();
        return guest;
    }
}
