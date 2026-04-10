package com.vocawik.module.logging.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.logging.config.LoggingHttpProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private final ClientIpResolver clientIpResolver =
            new ClientIpResolver(
                    new LoggingHttpProperties(
                            "127.0.0.1/32,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128"));

    @Test
    @DisplayName("Should use X-Forwarded-For header only for trusted proxy IP")
    void resolve_withTrustedProxy_shouldUseHeaderIp() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("10.10.10.10");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 70.41.3.18");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("Should ignore X-Forwarded-For header for untrusted source IP")
    void resolve_withUntrustedSource_shouldUseRemoteAddr() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Should prefer Forwarded header from trusted proxy")
    void resolve_withForwardedHeader_shouldUseForwardedFor() {
        MockHttpServletRequest request = request();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=\"198.51.100.20:443\";proto=https");

        String clientIp = clientIpResolver.resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.20");
    }

    private MockHttpServletRequest request() {
        return new MockHttpServletRequest("GET", "/api/v1/users");
    }
}
