package com.vocawik.domain.song;

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

/** External link metadata for a song. */
@Getter
@Entity
@Table(name = "song_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 20)
    private SongLinkType linkType;

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
     * Creates a new song link row.
     *
     * @param song parent song
     * @param linkType link type
     * @param url target url
     * @return created song link
     */
    public static SongLink create(Song song, SongLinkType linkType, String url) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (linkType == null) {
            throw new IllegalArgumentException("linkType is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        SongLink songLink = new SongLink();
        songLink.song = song;
        songLink.linkType = linkType;
        songLink.url = url;
        return songLink;
    }
}
