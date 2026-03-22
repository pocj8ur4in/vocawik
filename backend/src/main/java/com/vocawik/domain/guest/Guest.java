package com.vocawik.domain.guest;

import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Guest identity aggregate root entity. */
@Getter
@Entity
@Table(name = "guests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest extends BaseEntity {

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuestStatus status = GuestStatus.ACTIVE;

    @Getter(AccessLevel.NONE)
    @Column(name = "ip", columnDefinition = "inet")
    private InetAddress ip;

    @Column private LocalDateTime lastSeenAt;

    /**
     * Creates a new guest from client IP address.
     *
     * @param ip client IP address
     * @return created guest instance
     */
    public static Guest create(String ip) {
        return create(parseIp(ip));
    }

    /**
     * Creates a new guest from client IP address.
     *
     * @param ip client IP address
     * @return created guest instance
     */
    public static Guest create(InetAddress ip) {
        Guest guest = new Guest();
        guest.ip = ip;
        guest.lastSeenAt = LocalDateTime.now();
        return guest;
    }

    /** Returns the normalized client IP address. */
    public String getIp() {
        return ip == null ? null : ip.getHostAddress();
    }

    /** Updates guest last-seen timestamp. */
    public void touchLastSeenAt() {
        this.lastSeenAt = LocalDateTime.now();
    }

    private static InetAddress parseIp(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("Guest IP must not be blank");
        }
        try {
            return InetAddress.getByName(ip.trim());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid guest IP: " + ip, e);
        }
    }
}
