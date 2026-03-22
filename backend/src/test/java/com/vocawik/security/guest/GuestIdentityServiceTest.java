package com.vocawik.security.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocawik.domain.guest.Guest;
import com.vocawik.repository.guest.GuestRepository;
import java.net.InetAddress;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuestIdentityServiceTest {

    @Test
    @DisplayName("Should resolve guests using InetAddress repository lookups")
    void findOrCreateByIp_shouldUseInetAddressLookup() throws Exception {
        GuestRepository guestRepository = mock(GuestRepository.class);
        GuestIdentityService guestIdentityService = new GuestIdentityService(guestRepository);
        InetAddress ip = InetAddress.getByName("203.0.113.10");
        Guest guest = Guest.create(ip);

        when(guestRepository.findByIpAndIsDeletedFalse(ip)).thenReturn(Optional.of(guest));

        Guest result = guestIdentityService.findOrCreateByIp("203.0.113.10");

        assertThat(result).isSameAs(guest);
        verify(guestRepository).findByIpAndIsDeletedFalse(ip);
    }

    @Test
    @DisplayName("Should save newly created guests with parsed InetAddress values")
    void findOrCreateByIp_shouldPersistParsedInetAddress() throws Exception {
        GuestRepository guestRepository = mock(GuestRepository.class);
        GuestIdentityService guestIdentityService = new GuestIdentityService(guestRepository);
        InetAddress ip = InetAddress.getByName("2001:db8::10");

        when(guestRepository.findByIpAndIsDeletedFalse(ip)).thenReturn(Optional.empty());
        when(guestRepository.save(any(Guest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Guest.class));

        Guest result = guestIdentityService.findOrCreateByIp("2001:db8::10");

        assertThat(result.getIp()).isEqualTo("2001:db8:0:0:0:0:0:10");
        verify(guestRepository).findByIpAndIsDeletedFalse(ip);
        verify(guestRepository).save(any(Guest.class));
    }

    @Test
    @DisplayName("Should reject invalid guest IP values")
    void findOrCreateByIp_shouldRejectInvalidIp() {
        GuestRepository guestRepository = mock(GuestRepository.class);
        GuestIdentityService guestIdentityService = new GuestIdentityService(guestRepository);

        assertThatThrownBy(() -> guestIdentityService.findOrCreateByIp("not-an-ip"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid guest IP");
    }
}
