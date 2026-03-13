package com.vocawik.security.ip;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Hashes client IP addresses with the configured salt. */
@Component
public class IpHashService {

    @Value("${guest.ip-hash-salt:}")
    private String ipHashSalt;

    /**
     * Returns a salted SHA-256 hash for the given client IP.
     *
     * @param ip client IP address
     * @return hashed IP
     */
    public String hash(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip is required");
        }
        return sha256Hex((ipHashSalt == null ? "" : ipHashSalt) + "|" + ip);
    }

    private String sha256Hex(String input) {
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
