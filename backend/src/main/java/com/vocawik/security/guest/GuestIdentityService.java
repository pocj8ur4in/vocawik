package com.vocawik.security.guest;

import com.vocawik.domain.guest.Guest;
import com.vocawik.repository.guest.GuestRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates or resolves a guest identity from a client IP address. */
@Service
@RequiredArgsConstructor
public class GuestIdentityService {

    private final GuestRepository guestRepository;

    @Value("${guest.ip-hash-salt:}")
    private String ipHashSalt;

    /**
     * Resolves (or creates) a guest identity from a client IP address.
     *
     * @param ip client IP address
     * @return existing or newly created guest
     */
    @Transactional
    public Guest findOrCreateByIp(String ip) {
        String ipHash = sha256Hex((ipHashSalt == null ? "" : ipHashSalt) + "|" + ip);
        Guest guest;
        try {
            guest =
                    guestRepository
                            .findByIpHash(ipHash)
                            .orElseGet(() -> guestRepository.save(Guest.create(ipHash)));
        } catch (DataIntegrityViolationException ex) {
            guest = guestRepository.findByIpHash(ipHash).orElseThrow(() -> ex);
        }
        guest.touchLastSeenAt();
        return guest;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
