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

/** External link entity for an artist resource. */
@Getter
@Entity
@Table(name = "artist_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ArtistLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    @Enumerated(EnumType.STRING)
    @Column(name = "artist_link_type", nullable = false, length = 20)
    private ArtistLinkType artistLinkType;

    @Column(nullable = false)
    private String url;

    @Column(name = "content")
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * Creates a new artist link.
     *
     * @param artist owner artist
     * @param artistLinkType link type
     * @param url external link url
     * @param content link note/description
     * @param isDeleted deletion flag
     * @return created artist link
     */
    public static ArtistLink create(
            Artist artist,
            ArtistLinkType artistLinkType,
            String url,
            String content,
            boolean isDeleted) {
        if (artist == null) {
            throw new IllegalArgumentException("artist is required");
        }
        if (artistLinkType == null) {
            throw new IllegalArgumentException("artistLinkType is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        ArtistLink artistLink = new ArtistLink();
        artistLink.artist = artist;
        artistLink.artistLinkType = artistLinkType;
        artistLink.url = url.trim();
        artistLink.content = content;
        artistLink.isDeleted = isDeleted;
        return artistLink;
    }

    /** Soft-deletes this link. */
    public void softDelete() {
        this.isDeleted = true;
    }

    /**
     * Updates deleted flag.
     *
     * @param isDeleted updated deleted flag
     */
    public void updateDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    /**
     * Updates content and deleted flag.
     *
     * @param content updated link content
     * @param isDeleted updated deleted flag
     */
    public void update(String content, boolean isDeleted) {
        this.content = content;
        this.isDeleted = isDeleted;
    }
}
