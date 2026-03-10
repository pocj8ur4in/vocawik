package com.vocawik.domain.history;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only history row for resource revisions. */
@Getter
@Entity
@Table(name = "histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class History extends BaseEntity {
    private static final int HASH_LENGTH = 64;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Column(nullable = false)
    private int revision;

    @Column(name = "base_revision", nullable = false)
    private int baseRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private HistoryActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_guest_id")
    private Guest actorGuest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "jsonb")
    private JsonNode snapshotData;

    @Column(name = "content_hash", nullable = false, length = HASH_LENGTH)
    private String contentHash;

    /**
     * Creates a snapshot-only history row.
     *
     * @param resource target resource
     * @param revision resource revision number
     * @param baseRevision previous revision number
     * @param actionType action type
     * @param actorUser actor user (nullable)
     * @param actorGuest actor guest (nullable)
     * @param snapshotData full snapshot payload
     * @param contentHash hash of resulting full content
     * @return created history row
     */
    public static History createSnapshot(
            Resource resource,
            int revision,
            int baseRevision,
            HistoryActionType actionType,
            User actorUser,
            Guest actorGuest,
            JsonNode snapshotData,
            String contentHash) {
        validate(resource, revision, baseRevision, actionType, actorUser, actorGuest, contentHash);
        if (snapshotData == null) {
            throw new IllegalArgumentException("snapshotData is required for snapshot row");
        }

        History history = new History();
        history.resource = resource;
        history.revision = revision;
        history.baseRevision = baseRevision;
        history.actionType = actionType;
        history.actorUser = actorUser;
        history.actorGuest = actorGuest;
        history.snapshotData = snapshotData;
        history.contentHash = contentHash;
        return history;
    }

    private static void validate(
            Resource resource,
            int revision,
            int baseRevision,
            HistoryActionType actionType,
            User actorUser,
            Guest actorGuest,
            String contentHash) {
        if (resource == null) {
            throw new IllegalArgumentException("resource is required");
        }
        if (revision <= 0) {
            throw new IllegalArgumentException("revision must be > 0");
        }
        if (baseRevision < 0 || baseRevision >= revision) {
            throw new IllegalArgumentException("baseRevision must be >= 0 and < revision");
        }
        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
        if (actorUser != null && actorGuest != null) {
            throw new IllegalArgumentException("actorUser and actorGuest cannot both be set");
        }
        if (contentHash == null || contentHash.isBlank() || contentHash.length() != HASH_LENGTH) {
            throw new IllegalArgumentException("contentHash must be 64 chars");
        }
    }
}
