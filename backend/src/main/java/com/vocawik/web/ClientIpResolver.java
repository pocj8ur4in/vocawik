package com.vocawik.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves client IP with trusted proxy CIDR validation. */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final List<CidrRange> trustedProxyRanges;

    /**
     * Creates a client IP resolver with trusted proxy CIDR configuration.
     *
     * @param trustedProxyCidrs comma-separated trusted proxy CIDR ranges
     */
    public ClientIpResolver(
            @Value(
                            "${logging.http.trusted-proxy-cidrs:127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128}")
                    String trustedProxyCidrs) {
        this.trustedProxyRanges = parseTrustedProxyCidrs(trustedProxyCidrs);
    }

    /**
     * Returns client IP from {@code X-Forwarded-For} only when the request source is trusted.
     *
     * @param request HTTP request
     * @return resolved client IP
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return remoteAddr;
    }

    private List<CidrRange> parseTrustedProxyCidrs(String raw) {
        List<CidrRange> ranges = new ArrayList<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(
                        cidr -> {
                            try {
                                ranges.add(CidrRange.parse(cidr));
                            } catch (IllegalArgumentException e) {
                                logger.warn("Ignoring invalid trusted proxy CIDR: {}", cidr);
                            }
                        });
        return ranges;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            for (CidrRange range : trustedProxyRanges) {
                if (range.matches(address)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            logger.debug("Cannot parse remote address: {}", ip);
        }
        return false;
    }

    private record CidrRange(byte[] network, int prefixLength) {
        private static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr);
            }
            try {
                InetAddress networkAddress = InetAddress.getByName(parts[0]);
                int prefix = Integer.parseInt(parts[1]);
                int maxPrefix = networkAddress.getAddress().length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException("Invalid CIDR prefix: " + cidr);
                }
                return new CidrRange(networkAddress.getAddress(), prefix);
            } catch (UnknownHostException | NumberFormatException e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
            }
        }

        private boolean matches(InetAddress address) {
            byte[] target = address.getAddress();
            if (target.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (target[i] != network[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (target[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
