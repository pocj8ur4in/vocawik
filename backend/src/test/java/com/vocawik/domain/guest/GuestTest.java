package com.vocawik.domain.guest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuestTest {

    @Test
    @DisplayName("Should expose normalized IP string for stored InetAddress values")
    void getIp_shouldReturnNormalizedIpString() {
        Guest guest = Guest.create("203.0.113.10");

        assertThat(guest.getIp()).isEqualTo("203.0.113.10");
    }
}
