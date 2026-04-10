package com.vocawik.module.web.clientip;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Resolves the effective client IP using trusted proxy CIDR validation. */
@Slf4j
@Component
public class ClientIpResolver {

    // Forwarding headers are trusted only when the direct peer matches a trusted proxy CIDR.
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String FORWARDED = "Forwarded";
    private static final String UNKNOWN = "unknown";

    private final List<CidrRange> trustedProxyRanges;

    /**
     * Creates a resolver with trusted proxy CIDR configuration.
     *
     * @param properties client IP resolution properties
     */
    public ClientIpResolver(WebClientIpProperties properties) {
        this.trustedProxyRanges = parseTrustedProxyCidrs(properties.trustedProxyCidrs());
    }

    /**
     * Resolves the client IP from forwarding headers only when the source address is trusted.
     *
     * @param request HTTP request
     * @return resolved client IP or {@code unknown}
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String remoteAddr = normalizeIpLiteral(request.getRemoteAddr());
        if (remoteAddr == null) {
            return UNKNOWN;
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

    /**
     * Extracts the client IP from the RFC 7239 {@code Forwarded} header by reading the first valid
     * {@code for} parameter.
     *
     * <p>For example, {@code for="198.51.100.20:443";proto=https} resolves to {@code
     * 198.51.100.20}, and {@code for="[2001:db8::1]"} resolves to the normalized IPv6 address.
     *
     * @param forwarded raw {@code Forwarded} header value
     * @return first valid {@code for} address, or null if none can be parsed
     */
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

    /**
     * Normalizes an IP literal by removing surrounding quotes and resolving it to a canonical form.
     *
     * @param rawValue the raw IP literal
     * @return the normalized IP address or null if invalid
     */
    private String normalizeIpLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if (value.isEmpty() || UNKNOWN.equalsIgnoreCase(value)) {
            return null;
        }

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
            return InetAddress.getByName(host).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * Checks if the given IP address belongs to any of the trusted proxy CIDRs.
     *
     * @param ip the IP address to check
     * @return true if the IP is from a trusted proxy, false otherwise
     */
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

    /**
     * Parses configured trusted proxy CIDRs.
     *
     * <p>For example, {@code "192.168.0.0/24, ::1/128"} is parsed into two ranges: network {@code
     * 192.168.0.0} with prefix {@code 24}, and network {@code ::1} with prefix {@code 128}.
     *
     * @param raw comma-separated CIDR notation
     * @return parsed CIDR ranges
     */
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

    /**
     * CIDR range backed by a network address and prefix length.
     *
     * <p>The network address is stored as raw IPv4 or IPv6 bytes, and {@link #matches(InetAddress)}
     * compares only the prefix bits covered by the CIDR mask.
     *
     * @param network raw bytes of the CIDR network address
     * @param prefixLength number of leading bits that must match
     */
    private record CidrRange(byte[] network, int prefixLength) {

        /**
         * Parses a CIDR notation string into a {@link CidrRange}.
         *
         * @param cidr CIDR notation such as {@code 192.168.0.0/24} or {@code ::1/128}
         * @return parsed CIDR range
         * @throws IllegalArgumentException if the notation or prefix length is invalid
         */
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

        /**
         * Checks whether the given IP address belongs to this CIDR range.
         *
         * @param address IP address to compare against this range
         * @return true when the address family and prefix bits match
         */
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
