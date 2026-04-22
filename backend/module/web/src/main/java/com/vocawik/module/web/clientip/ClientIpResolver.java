package com.vocawik.module.web.clientip;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves the effective client IP using trusted proxy CIDR validation. */
@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String UNKNOWN = "unknown";
    private static final int MAX_X_FORWARDED_FOR_LENGTH = 2_048;
    private static final int MAX_PROXY_HOPS = 16;

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
     * Resolves the client IP from {@code X-Forwarded-For} only when the direct peer is trusted.
     *
     * <p>The chain is inspected from the direct peer towards the client. Trusted proxy hops are
     * discarded from the right, and the first untrusted address is treated as the client. Malformed
     * or excessively long chains are ignored in favor of the direct peer address.
     *
     * @param request HTTP request
     * @return resolved client IP or {@code unknown}
     */
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        InetAddress remoteAddress = parseIpLiteral(request.getRemoteAddr());
        if (remoteAddress == null) {
            return UNKNOWN;
        }
        if (!isTrustedProxy(remoteAddress)) {
            return remoteAddress.getHostAddress();
        }

        String xForwardedFor = getSingleHeaderValue(request, X_FORWARDED_FOR);
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteAddress.getHostAddress();
        }
        if (xForwardedFor.length() > MAX_X_FORWARDED_FOR_LENGTH) {
            return remoteAddress.getHostAddress();
        }

        String[] tokens = xForwardedFor.split(",", -1);
        if (tokens.length > MAX_PROXY_HOPS) {
            return remoteAddress.getHostAddress();
        }

        List<InetAddress> chain = new ArrayList<>(tokens.length + 1);
        for (String token : tokens) {
            InetAddress address = parseIpLiteral(token);
            if (address == null) {
                return remoteAddress.getHostAddress();
            }
            chain.add(address);
        }
        chain.add(remoteAddress);

        for (int index = chain.size() - 1; index >= 0; index--) {
            InetAddress address = chain.get(index);
            if (!isTrustedProxy(address)) {
                return address.getHostAddress();
            }
        }
        return chain.get(0).getHostAddress();
    }

    /**
     * Returns the only value of the requested header.
     *
     * <p>Multiple header fields are treated as ambiguous and rejected.
     *
     * @param request HTTP request containing the header
     * @param headerName header name to read
     * @return the single header value, or null when absent or repeated
     */
    private static String getSingleHeaderValue(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }

        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }

    /**
     * Parses an IPv4 or IPv6 literal without allowing hostname resolution.
     *
     * <p>IPv4 addresses may include a port, while IPv6 addresses with a port must use brackets.
     * Zone identifiers are rejected because forwarding headers cross host boundaries.
     *
     * @param rawValue raw address value
     * @return parsed address, or null if the value is not a valid IP literal
     */
    private static InetAddress parseIpLiteral(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String value = rawValue.trim();
        if (value.isEmpty() || UNKNOWN.equalsIgnoreCase(value) || value.length() > 64) {
            return null;
        }

        String host;
        if (value.startsWith("[")) {
            int closingBracket = value.indexOf(']');
            if (closingBracket <= 1 || !isValidPortSuffix(value.substring(closingBracket + 1))) {
                return null;
            }
            host = value.substring(1, closingBracket);
        } else {
            int firstColon = value.indexOf(':');
            int lastColon = value.lastIndexOf(':');
            if (firstColon > 0
                    && firstColon == lastColon
                    && value.substring(0, firstColon).contains(".")) {
                if (!isValidPortSuffix(value.substring(firstColon))) {
                    return null;
                }
                host = value.substring(0, firstColon);
            } else {
                host = value;
            }
        }

        return parseBareIpLiteral(host);
    }

    /**
     * Parses an unwrapped IPv4 or IPv6 literal without allowing ports or zone identifiers.
     *
     * @param host raw address literal
     * @return parsed address, or null if the value is invalid
     */
    private static InetAddress parseBareIpLiteral(String host) {
        if (host == null || host.isEmpty() || host.length() > 45 || host.indexOf('%') >= 0) {
            return null;
        }
        if (host.indexOf(':') >= 0) {
            return parseIpv6Literal(host);
        }
        return parseIpv4Literal(host);
    }

    /**
     * Validates an optional decimal port suffix.
     *
     * @param suffix empty text or a colon-prefixed port
     * @return whether the suffix is empty or contains a port from 0 through 65535
     */
    private static boolean isValidPortSuffix(String suffix) {
        if (suffix.isEmpty()) {
            return true;
        }
        if (suffix.charAt(0) != ':' || suffix.length() == 1) {
            return false;
        }

        int port = 0;
        for (int index = 1; index < suffix.length(); index++) {
            char character = suffix.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
            port = port * 10 + (character - '0');
            if (port > 65_535) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a strict dotted-decimal IPv4 literal without hostname resolution.
     *
     * @param value raw IPv4 literal
     * @return parsed address, or null if the value is invalid
     * @throws IllegalStateException if the runtime rejects the validated IPv4 byte length
     */
    private static InetAddress parseIpv4Literal(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }

        byte[] address = new byte[4];
        for (int index = 0; index < octets.length; index++) {
            String octet = octets[index];
            if (octet.isEmpty()
                    || octet.length() > 3
                    || (octet.length() > 1 && octet.charAt(0) == '0')) {
                return null;
            }

            int valueOfOctet = 0;
            for (int characterIndex = 0; characterIndex < octet.length(); characterIndex++) {
                char character = octet.charAt(characterIndex);
                if (character < '0' || character > '9') {
                    return null;
                }
                valueOfOctet = valueOfOctet * 10 + (character - '0');
            }
            if (valueOfOctet > 255) {
                return null;
            }
            address[index] = (byte) valueOfOctet;
        }

        try {
            return InetAddress.getByAddress(address);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException("Unexpected IPv4 address length", impossible);
        }
    }

    /**
     * Parses an IPv6 literal after restricting input to numeric address characters.
     *
     * @param value raw IPv6 literal
     * @return parsed address, or null if the value is invalid
     */
    private static InetAddress parseIpv6Literal(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean hexadecimal =
                    character >= '0' && character <= '9'
                            || character >= 'a' && character <= 'f'
                            || character >= 'A' && character <= 'F';
            if (!hexadecimal && character != ':' && character != '.') {
                return null;
            }
        }

        try {
            // A colon and the character allow-list above guarantee this cannot be a hostname.
            return InetAddress.getByName(value);
        } catch (UnknownHostException invalidLiteral) {
            return null;
        }
    }

    /**
     * Returns whether an address belongs to a configured trusted proxy range.
     *
     * @param address address to check
     * @return whether the address is a trusted proxy
     */
    private boolean isTrustedProxy(InetAddress address) {
        for (CidrRange range : trustedProxyRanges) {
            if (range.matches(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the comma-separated trusted proxy CIDR configuration.
     *
     * @param raw configured CIDR list
     * @return parsed trusted proxy ranges
     * @throws IllegalArgumentException if any configured CIDR is invalid
     */
    private List<CidrRange> parseTrustedProxyCidrs(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",", -1)).map(String::trim).map(CidrRange::parse).toList();
    }

    /**
     * Represents an IPv4 or IPv6 network range used to identify trusted proxies.
     *
     * @param network raw network address bytes
     * @param prefixLength number of leading network bits
     */
    private record CidrRange(byte[] network, int prefixLength) {

        /**
         * Parses a CIDR expression without allowing hostname resolution.
         *
         * @param cidr CIDR expression to parse
         * @return parsed CIDR range
         * @throws IllegalArgumentException if the expression or prefix is invalid
         */
        private static CidrRange parse(String cidr) {
            String[] parts = cidr.split("/", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
            }

            InetAddress networkAddress = parseBareIpLiteral(parts[0]);
            if (networkAddress == null) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
            }

            try {
                int prefix = Integer.parseInt(parts[1]);
                int maxPrefix = networkAddress.getAddress().length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + cidr);
                }
                return new CidrRange(networkAddress.getAddress(), prefix);
            } catch (NumberFormatException invalidPrefix) {
                throw new IllegalArgumentException(
                        "Invalid trusted proxy CIDR: " + cidr, invalidPrefix);
            }
        }

        /**
         * Checks whether an address is contained in this network range.
         *
         * @param address address to compare
         * @return whether the address matches the configured network prefix
         */
        private boolean matches(InetAddress address) {
            byte[] target = address.getAddress();
            if (target.length != network.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (target[index] != network[index]) {
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
