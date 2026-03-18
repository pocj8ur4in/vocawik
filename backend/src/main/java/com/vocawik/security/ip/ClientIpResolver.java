package com.vocawik.security.ip;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves client IP with trusted proxy CIDR validation. */
@Slf4j
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String FORWARDED = "Forwarded";

    private final List<CidrRange> trustedProxyRanges;

    /**
     * Creates a client IP resolver with trusted proxy CIDR configuration.
     *
     * @param trustedProxyCidrs comma-separated trusted proxy CIDR ranges
     */
    public ClientIpResolver(
            @Value(
                            "${security.client-ip.trusted-proxy-cidrs:127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128}")
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
        String remoteAddr = normalizeIpLiteral(request.getRemoteAddr());
        if (remoteAddr == null) {
            return null;
        }
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String forwarded = request.getHeader(FORWARDED);
        String fromForwarded = extractIpFromForwarded(forwarded);
        if (fromForwarded != null) {
            return fromForwarded;
        }

        String xForwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            for (String token : xForwardedFor.split(",")) {
                String candidate = normalizeIpLiteral(token);
                if (candidate != null) {
                    return candidate;
                }
            }
        }

        String xRealIp = normalizeIpLiteral(request.getHeader(X_REAL_IP));
        if (xRealIp != null) {
            return xRealIp;
        }
        return remoteAddr;
    }

    private String extractIpFromForwarded(String forwarded) {
        if (forwarded == null || forwarded.isBlank()) {
            return null;
        }
        String[] entries = forwarded.split(",");
        for (String entry : entries) {
            String[] params = entry.split(";");
            for (String param : params) {
                String trimmed = param.trim();
                if (!trimmed.toLowerCase(Locale.ROOT).startsWith("for=")) {
                    continue;
                }
                String value = trimmed.substring(4).trim();
                String candidate = normalizeIpLiteral(value);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private String normalizeIpLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("unknown")) {
            return null;
        }

        // RFC 7239 quoted-string support: for="[2001:db8::1]:4711"
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1).trim();
        }

        String host = value;
        if (host.startsWith("[")) {
            int closing = host.indexOf(']');
            if (closing <= 1) {
                return null;
            }
            host = host.substring(1, closing);
        } else if (host.chars().filter(ch -> ch == ':').count() == 1 && host.contains(".")) {
            // IPv4 with port (e.g. 203.0.113.10:443)
            host = host.substring(0, host.lastIndexOf(':'));
        }

        int zoneIndex = host.indexOf('%');
        if (zoneIndex > 0) {
            host = host.substring(0, zoneIndex);
        }
        if (host.isBlank()) {
            return null;
        }

        try {
            InetAddress address = InetAddress.getByName(host);
            return address.getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
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
