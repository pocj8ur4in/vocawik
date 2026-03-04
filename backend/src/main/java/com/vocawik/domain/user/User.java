package com.vocawik.domain.user;

import com.vocawik.common.i18n.Language;
import com.vocawik.common.jpa.converter.EmailAttributeConverter;
import com.vocawik.common.jpa.converter.ZoneIdAttributeConverter;
import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** User identity aggregate root entity. */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Convert(converter = EmailAttributeConverter.class)
    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang_code", nullable = false, length = 10)
    private Language langCode = Language.UND;

    @Convert(converter = ZoneIdAttributeConverter.class)
    @Column(nullable = false, length = 40)
    private ZoneId timezone = ZoneId.of("UTC");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserTheme theme = UserTheme.UND;

    @Enumerated(EnumType.STRING)
    @Column(name = "pv_provider", nullable = false, length = 20)
    private UserPvProvider pvProvider = UserPvProvider.UND;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column private LocalDateTime lastLoginAt;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @Column(name = "password_failed_attempts", nullable = false)
    private int passwordFailedAttempts;

    @Column(name = "password_locked_at")
    private LocalDateTime passwordLockedAt;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "user")
    private List<UserAuthProvider> authProviders = new ArrayList<>();

    /**
     * Creates a new user.
     *
     * @param email user email
     * @param nickname display nickname
     * @param langCode language code
     * @param timezone timezone
     * @param theme ui theme
     * @param pvProvider preferred pv provider
     * @param role user role
     * @return created user instance
     */
    public static User create(
            String email,
            String nickname,
            Language langCode,
            ZoneId timezone,
            UserTheme theme,
            UserPvProvider pvProvider,
            UserRole role) {
        User user = new User();
        user.email = email;
        user.nickname = nickname;
        user.langCode = langCode == null ? Language.UND : langCode;
        user.timezone = timezone == null ? ZoneId.of("UTC") : timezone;
        user.theme = theme == null ? UserTheme.UND : theme;
        user.pvProvider = pvProvider == null ? UserPvProvider.UND : pvProvider;
        user.role = role == null ? UserRole.USER : role;
        return user;
    }

    /** Updates last login timestamp. */
    public void touchLastLoginAt() {
        this.lastLoginAt = LocalDateTime.now();
    }

    /** Marks this account as active. */
    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Sets password hash and updates password metadata.
     *
     * @param passwordHash encoded password hash
     */
    public void setPassword(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash is required");
        }
        this.passwordHash = passwordHash;
        this.passwordUpdatedAt = LocalDateTime.now();
        clearPasswordFailureState();
    }

    /**
     * Returns whether email/password login is currently locked.
     *
     * @param now current timestamp
     * @return true when lock is active
     */
    public boolean isPasswordLocked(LocalDateTime now) {
        return passwordLockedAt != null && passwordLockedAt.isAfter(now);
    }

    /**
     * Clears counters when an existing lock has already expired.
     *
     * @param now current timestamp
     */
    public void clearPasswordLockIfExpired(LocalDateTime now) {
        if (passwordLockedAt != null && !passwordLockedAt.isAfter(now)) {
            clearPasswordFailureState();
        }
    }

    /**
     * Records one failed attempt and applies lock when threshold is reached.
     *
     * @param maxAttempts lock threshold
     * @param lockDuration lock duration
     * @param now current timestamp
     */
    public void recordPasswordFailure(int maxAttempts, Duration lockDuration, LocalDateTime now) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration must be > 0");
        }

        int nextFailedAttempts = passwordFailedAttempts + 1;
        if (nextFailedAttempts >= maxAttempts) {
            passwordFailedAttempts = 0;
            passwordLockedAt = now.plus(lockDuration);
            return;
        }
        passwordFailedAttempts = nextFailedAttempts;
    }

    /** Resets counter and removes active lock metadata. */
    public void clearPasswordFailureState() {
        passwordFailedAttempts = 0;
        passwordLockedAt = null;
    }
}
