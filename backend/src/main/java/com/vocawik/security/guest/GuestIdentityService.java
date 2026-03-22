package com.vocawik.security.guest;

import com.vocawik.domain.guest.Guest;
import com.vocawik.repository.guest.GuestRepository;
import java.net.InetAddress;
import java.net.UnknownHostException;
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
        InetAddress parsedIp = parseIp(ip);
        Guest guest;
        try {
            guest =
                    guestRepository
                            .findByIpAndIsDeletedFalse(parsedIp)
                            .orElseGet(() -> guestRepository.save(Guest.create(parsedIp)));
        } catch (DataIntegrityViolationException ex) {
            guest = guestRepository.findByIpAndIsDeletedFalse(parsedIp).orElseThrow(() -> ex);
        }
        guest.touchLastSeenAt();
        return guest;
    }

    private InetAddress parseIp(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("Guest IP must not be blank");
        }
        try {
            return InetAddress.getByName(ip.trim());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid guest IP: " + ip, e);
        }
    }
}
