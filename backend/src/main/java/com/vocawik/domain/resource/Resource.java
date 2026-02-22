package com.vocawik.domain.resource;

import com.vocawik.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    /**
     * Creates a new resource record.
     *
     * @param resourceType resource kind
     * @return created resource
     */
    public static Resource create(ResourceType resourceType) {
        Resource resource = new Resource();
        resource.resourceType = resourceType;
        return resource;
    }

    /** Soft deletes this resource. */
    public void softDelete() {
        this.isDeleted = true;
    }
}
