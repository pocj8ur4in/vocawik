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

/** External link entity for a song resource. */
@Getter
@Entity
@Table(name = "song_links")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(name = "song_link_type", nullable = false, length = 20)
    private SongLinkType songLinkType;

    @Column(nullable = false)
    private String url;

    @Column(name = "content")
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    /**
     * Creates a new song link.
     *
     * @param song owner song
     * @param songLinkType link type
     * @param url external link url
     * @param content link note/description
     * @param isDeleted deletion flag
     * @return created song link
     */
    public static SongLink create(
            Song song, SongLinkType songLinkType, String url, String content, boolean isDeleted) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (songLinkType == null) {
            throw new IllegalArgumentException("songLinkType is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        SongLink songLink = new SongLink();
        songLink.song = song;
        songLink.songLinkType = songLinkType;
        songLink.url = url.trim();
        songLink.content = content;
        songLink.isDeleted = isDeleted;
        return songLink;
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
