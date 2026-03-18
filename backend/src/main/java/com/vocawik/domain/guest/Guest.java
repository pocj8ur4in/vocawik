package com.vocawik.domain.guest;

import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
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

    @Column(name = "ip", columnDefinition = "inet")
    private String ip;

    @Column private LocalDateTime lastSeenAt;

    /**
     * Creates a new guest from client IP address.
     *
     * @param ip client IP address
     * @return created guest instance
     */
    public static Guest create(String ip) {
        Guest guest = new Guest();
        guest.ip = ip;
        guest.lastSeenAt = LocalDateTime.now();
        return guest;
    }

    /** Updates guest last-seen timestamp. */
    public void touchLastSeenAt() {
        this.lastSeenAt = LocalDateTime.now();
    }
}
