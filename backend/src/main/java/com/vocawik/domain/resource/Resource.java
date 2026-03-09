package com.vocawik.domain.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Resource root entity supported on polymorphic. */
@Getter
@Entity
@Table(name = "resources")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resource extends BaseEntity {

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceStatus status = ResourceStatus.ACTIVE;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb")
    private JsonNode data;

    /**
     * Creates a new resource record.
     *
     * @param resourceType resource kind
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url
     * @return created resource
     */
    public static Resource create(
            ResourceType resourceType, String canonicalName, String thumbnailUrl) {
        if (resourceType == null) {
            throw new IllegalArgumentException("resourceType is required");
        }
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName is required");
        }

        Resource resource = new Resource();
        resource.resourceType = resourceType;
        resource.canonicalName = canonicalName;
        resource.thumbnailUrl = thumbnailUrl;
        return resource;
    }

    /** Soft deletes this resource. */
    public void softDelete() {
        this.isDeleted = true;
    }

    /**
     * Updates json payload for read-model.
     *
     * @param data denormalized json payload
     */
    public void updateData(JsonNode data) {
        this.data = data;
    }

    /**
     * Updates canonical display name.
     *
     * @param canonicalName updated canonical name
     */
    public void updateCanonicalName(String canonicalName) {
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName is required");
        }
        this.canonicalName = canonicalName;
    }

    /**
     * Updates thumbnail url.
     *
     * @param thumbnailUrl updated thumbnail url
     */
    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
