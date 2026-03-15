package com.vocawik.domain.vocal;

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

/** External link entity for a vocal resource. */
@Getter
@Entity
@Table(name = "vocal_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VocalLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocal_id", nullable = false)
    private Vocal vocal;

    @Enumerated(EnumType.STRING)
    @Column(name = "vocal_link_type", nullable = false, length = 20)
    private VocalLinkType vocalLinkType;

    @Column(nullable = false)
    private String url;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * Creates a new vocal link.
     *
     * @param vocal owner vocal
     * @param vocalLinkType link type
     * @param url external link url
     * @param isDeleted deletion flag
     * @return created vocal link
     */
    public static VocalLink create(
            Vocal vocal, VocalLinkType vocalLinkType, String url, boolean isDeleted) {
        if (vocal == null) {
            throw new IllegalArgumentException("vocal is required");
        }
        if (vocalLinkType == null) {
            throw new IllegalArgumentException("vocalLinkType is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        VocalLink vocalLink = new VocalLink();
        vocalLink.vocal = vocal;
        vocalLink.vocalLinkType = vocalLinkType;
        vocalLink.url = url.trim();
        vocalLink.isDeleted = isDeleted;
        return vocalLink;
    }

    /** Soft-deletes this link. */
    public void softDelete() {
        this.isDeleted = true;
    }
}
