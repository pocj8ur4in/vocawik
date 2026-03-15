package com.vocawik.domain.resource;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.BaseEntity;
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

/** Localized display name for a resource. */
@Getter
@Entity
@Table(name = "resource_names")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceName extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @Enumerated(EnumType.STRING)
    @Column(name = "lang_code", nullable = false, length = 10)
    private Language langCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a localized name.
     *
     * @param resource target resource
     * @param langCode language code
     * @param name localized display name
     * @param isPrimary whether this name is primary in given language
     * @param sortOrder ordering within same language
     * @return created resource name
     */
    public static ResourceName create(
            Resource resource, Language langCode, String name, boolean isPrimary, int sortOrder) {
        if (resource == null) {
            throw new IllegalArgumentException("resource is required");
        }
        if (langCode == null) {
            throw new IllegalArgumentException("langCode is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        ResourceName resourceName = new ResourceName();
        resourceName.resource = resource;
        resourceName.langCode = langCode;
        resourceName.name = name;
        resourceName.isPrimary = isPrimary;
        resourceName.sortOrder = sortOrder;
        return resourceName;
    }

    /** Marks this name as primary. */
    public void markPrimary() {
        this.isPrimary = true;
    }

    /** Unmarks this name from primary. */
    public void unmarkPrimary() {
        this.isPrimary = false;
    }

    /**
     * Updates localized display name.
     *
     * @param name updated name
     */
    public void rename(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        this.name = name;
    }

    /**
     * Updates primary flag and display order.
     *
     * @param isPrimary updated primary flag
     * @param sortOrder updated sort order
     */
    public void updateDisplay(boolean isPrimary, int sortOrder) {
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.isPrimary = isPrimary;
        this.sortOrder = sortOrder;
    }
}
