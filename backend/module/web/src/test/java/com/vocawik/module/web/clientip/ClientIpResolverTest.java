package com.vocawik.module.web.clientip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final String TRUSTED_PROXY_CIDRS =
            "127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128";

    private final ClientIpResolver clientIpResolver =
            new ClientIpResolver(new WebClientIpProperties(TRUSTED_PROXY_CIDRS));

    @Test
    @DisplayName("Should remove trusted proxies from the right side of X-Forwarded-For")
    void resolve_withTrustedProxyChain_shouldReturnFirstUntrustedAddressFromRight() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "127.0.0.1, 203.0.113.50, 172.20.10.5");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("Should ignore forwarding headers for an untrusted direct peer")
    void resolve_withUntrustedSource_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("Forwarded", "for=127.0.0.1");
        request.addHeader("X-Real-IP", "127.0.0.1");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Should ignore Forwarded and X-Real-IP headers")
    void resolve_withAlternativeForwardingHeaders_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("Forwarded", "for=198.51.100.20");
        request.addHeader("X-Real-IP", "198.51.100.21");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.10.10.10");
    }

    @Test
    @DisplayName("Should ignore a malformed X-Forwarded-For chain")
    void resolve_withMalformedChain_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20, localhost");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.10.10.10");
    }

    @Test
    @DisplayName("Should reject an X-Forwarded-For chain with too many hops")
    void resolve_withTooManyHops_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader(
                "X-Forwarded-For", String.join(",", Collections.nCopies(17, "198.51.100.20")));

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.10.10.10");
    }

    @Test
    @DisplayName("Should reject an excessively long X-Forwarded-For header")
    void resolve_withLongHeader_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "1".repeat(2_049));

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.10.10.10");
    }

    @Test
    @DisplayName("Should reject multiple X-Forwarded-For header fields")
    void resolve_withMultipleHeaderFields_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");
        request.addHeader("X-Forwarded-For", "127.0.0.1");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("10.10.10.10");
    }

    @Test
    @DisplayName("Should support bracketed IPv6 addresses with ports")
    void resolve_withIpv6AddressAndPort_shouldReturnNormalizedAddress() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("::1");
        request.addHeader("X-Forwarded-For", "[2001:db8::1]:443");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("Should fail fast for a hostname in trusted proxy CIDR configuration")
    void constructor_withHostnameCidr_shouldThrowException() {
        WebClientIpProperties properties = new WebClientIpProperties("localhost/32");

        assertThatThrownBy(() -> new ClientIpResolver(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy CIDR");
    }

    @Test
    @DisplayName("Should fail fast for an invalid trusted proxy prefix")
    void constructor_withInvalidPrefix_shouldThrowException() {
        WebClientIpProperties properties = new WebClientIpProperties("10.0.0.0/33");

        assertThatThrownBy(() -> new ClientIpResolver(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy CIDR");
    }

    @Test
    @DisplayName("Should reject a port in trusted proxy CIDR configuration")
    void constructor_withPortInCidr_shouldThrowException() {
        WebClientIpProperties properties = new WebClientIpProperties("10.0.0.1:8080/32");

        assertThatThrownBy(() -> new ClientIpResolver(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy CIDR");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/users");
    }
}
