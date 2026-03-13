package com.vocawik.security.guest;

import com.vocawik.domain.guest.Guest;
import com.vocawik.repository.guest.GuestRepository;
import com.vocawik.security.ip.IpHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates or resolves a guest identity from a client IP address. */
@Service
@RequiredArgsConstructor
public class GuestIdentityService {

    private final GuestRepository guestRepository;
    private final IpHashService ipHashService;

    /**
     * Resolves (or creates) a guest identity from a client IP address.
     *
     * @param ip client IP address
     * @return existing or newly created guest
     */
    @Transactional
    public Guest findOrCreateByIp(String ip) {
        String ipHash = ipHashService.hash(ip);
        Guest guest;
        try {
            guest =
                    guestRepository
                            .findByIpHashAndIsDeletedFalse(ipHash)
                            .orElseGet(() -> guestRepository.save(Guest.create(ipHash)));
        } catch (DataIntegrityViolationException ex) {
            guest = guestRepository.findByIpHashAndIsDeletedFalse(ipHash).orElseThrow(() -> ex);
        }
        guest.touchLastSeenAt();
        return guest;
    }
}
