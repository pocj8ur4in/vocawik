package com.vocawik.domain.debate;

import com.vocawik.domain.BaseEntity;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Debate comment entity belonging to a debate thread. */
@Getter
@Entity
@Table(name = "debate_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DebateComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "debate_id", nullable = false)
    private Debate debate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private DebateComment parentComment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_guest_id")
    private Guest actorGuest;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * Creates a new debate comment.
     *
     * @param debate target debate
     * @param parentComment parent comment in the same debate, nullable
     * @param actorUser author user (nullable when guest actor is provided)
     * @param actorGuest author guest (nullable when user actor is provided)
     * @param content comment body
     * @return created debate comment
     */
    public static DebateComment create(
            Debate debate,
            DebateComment parentComment,
            User actorUser,
            Guest actorGuest,
            String content) {
        if (debate == null) {
            throw new IllegalArgumentException("debate is required");
        }
        if ((actorUser == null) == (actorGuest == null)) {
            throw new IllegalArgumentException("exactly one actor is required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        if (parentComment != null && parentComment.debate != debate) {
            throw new IllegalArgumentException("parent comment must belong to same debate");
        }

        DebateComment comment = new DebateComment();
        comment.debate = debate;
        comment.parentComment = parentComment;
        comment.actorUser = actorUser;
        comment.actorGuest = actorGuest;
        comment.content = content;
        return comment;
    }

    /**
     * Updates comment content with incrementing revision.
     *
     * @param content updated comment body
     */
    public void revise(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        this.content = content;
        this.revision += 1;
    }

    /** Soft deletes this comment. */
    public void softDelete() {
        this.isDeleted = true;
    }
}
