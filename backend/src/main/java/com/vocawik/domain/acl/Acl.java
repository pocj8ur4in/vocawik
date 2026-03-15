package com.vocawik.domain.acl;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.resource.Resource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** ACL rule entity for resource access control. */
@Getter
@Entity
@Table(name = "acls")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Acl extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AclAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 20)
    private AclSubjectType subjectType;

    @Column(name = "subject_value", nullable = false, length = 191)
    private String subjectValue = "";

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false, length = 10)
    private AclEffect effect = AclEffect.ALLOW;

    @Column(nullable = false)
    private int priority = 100;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Creates a new ACL rule.
     *
     * @param resource target resource
     * @param action action type
     * @param subjectType subject condition type
     * @param subjectValue subject condition value
     * @param effect allow or deny
     * @param priority rule priority
     * @param expiresAt optional expiration time
     * @return created ACL rule
     */
    public static Acl create(
            Resource resource,
            AclAction action,
            AclSubjectType subjectType,
            String subjectValue,
            AclEffect effect,
            int priority,
            LocalDateTime expiresAt) {
        Acl acl = new Acl();
        acl.resource = resource;
        acl.action = action;
        acl.subjectType = subjectType;
        acl.subjectValue = subjectValue == null ? "" : subjectValue;
        acl.effect = effect;
        acl.priority = priority;
        acl.expiresAt = expiresAt;
        return acl;
    }

    /**
     * Updates mutable rule fields.
     *
     * @param effect updated effect
     * @param expiresAt updated expiration time
     */
    public void updateRule(AclEffect effect, LocalDateTime expiresAt) {
        if (effect == null) {
            throw new IllegalArgumentException("effect is required");
        }
        this.effect = effect;
        this.expiresAt = expiresAt;
    }
}
