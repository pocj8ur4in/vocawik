package com.vocawik.domain.user;

import com.vocawik.common.auth.AuthProvider;
import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** OAuth identity linked to a user. */
@Getter
@Entity
@Table(
        name = "user_auth_providers",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_user_auth_provider_provider_provider_user_id",
                    columnNames = {"provider", "provider_user_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAuthProvider extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 191)
    private String providerUserId;

    @Column(length = 254)
    private String email;

    /**
     * Creates a provider mapping linked to a user.
     *
     * @param user linked user
     * @param provider provider type
     * @param providerUserId provider-side user identifier
     * @param email provider email
     * @return created provider mapping
     */
    public static UserAuthProvider link(
            User user, AuthProvider provider, String providerUserId, String email) {
        UserAuthProvider mapping = new UserAuthProvider();
        mapping.user = user;
        mapping.provider = provider;
        mapping.providerUserId = providerUserId;
        mapping.email = email;
        return mapping;
    }
}
