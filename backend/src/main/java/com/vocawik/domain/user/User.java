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

    @Column(nullable = false, length = 100)
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
}
