package com.vocawik.module.persistence;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Base JPA entity with identity and audit fields. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Creates an entity for JPA reconstruction. */
    protected BaseEntity() {}

    /** Assigns the external UUID before the entity is first persisted. */
    @PrePersist
    protected void initializeUuid() {
        if (uuid == null) {
            uuid = UuidCreator.getTimeOrderedEpoch();
        }
    }

    /**
     * Returns the database identifier.
     *
     * @return internal database identifier
     */
    public Long getId() {
        return id;
    }

    /**
     * Returns the external UUID.
     *
     * @return external UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Returns the UTC creation timestamp.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the UTC last-modified timestamp.
     *
     * @return last-modified timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
