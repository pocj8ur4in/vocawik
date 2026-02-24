package com.vocawik.domain.debate;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Debate thread entity attached to a resource. */
@Getter
@Entity
@Table(name = "debates")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Debate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_guest_id")
    private Guest actorGuest;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebateStatus status = DebateStatus.OPEN;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * Creates a new debate thread.
     *
     * @param resource target resource
     * @param actorUser author user (nullable when guest actor is provided)
     * @param actorGuest author guest (nullable when user actor is provided)
     * @param title thread title
     * @return created debate
     */
    public static Debate create(Resource resource, User actorUser, Guest actorGuest, String title) {
        if (resource == null) {
            throw new IllegalArgumentException("resource is required");
        }
        if ((actorUser == null) == (actorGuest == null)) {
            throw new IllegalArgumentException("exactly one actor is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }

        Debate debate = new Debate();
        debate.resource = resource;
        debate.actorUser = actorUser;
        debate.actorGuest = actorGuest;
        debate.title = title;
        return debate;
    }

    /** Closes this debate thread. */
    public void close() {
        this.status = DebateStatus.CLOSED;
    }

    /** Archives this debate thread. */
    public void archive() {
        this.status = DebateStatus.ARCHIVED;
    }

    /** Soft deletes this debate thread. */
    public void softDelete() {
        this.isDeleted = true;
    }
}
