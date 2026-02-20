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

    @Column(name = "ip_hash", nullable = false, length = 64, unique = true)
    private String ipHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuestStatus status = GuestStatus.ACTIVE;

    @Column private LocalDateTime lastSeenAt;

    public static Guest create(String ipHash) {
        Guest guest = new Guest();
        guest.ipHash = ipHash;
        guest.lastSeenAt = LocalDateTime.now();
        return guest;
    }

    public void touchLastSeenAt() {
        this.lastSeenAt = LocalDateTime.now();
    }
}
