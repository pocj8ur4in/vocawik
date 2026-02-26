package com.vocawik.domain.artist;

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

/** External link metadata for an artist. */
@Getter
@Entity
@Table(name = "artist_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private ArtistLinkType linkType;

    @Column(nullable = false)
    private String url;

    @Column(length = 255)
    private String title;

    @Column(name = "is_official", nullable = false)
    private boolean isOfficial;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a new artist link row.
     *
     * @param artist parent artist
     * @param linkType link type
     * @param url target url
     * @return created artist link
     */
    public static ArtistLink create(Artist artist, ArtistLinkType linkType, String url) {
        if (artist == null) {
            throw new IllegalArgumentException("artist is required");
        }
        if (linkType == null) {
            throw new IllegalArgumentException("linkType is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        ArtistLink artistLink = new ArtistLink();
        artistLink.artist = artist;
        artistLink.linkType = linkType;
        artistLink.url = url;
        return artistLink;
    }
}
