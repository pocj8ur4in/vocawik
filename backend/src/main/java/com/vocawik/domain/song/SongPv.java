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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Song PV metadata row. */
@Getter
@Entity
@Table(name = "song_pvs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SongPv extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Enumerated(EnumType.STRING)
    @Column(name = "service", nullable = false, length = 20)
    private SongPvProvider service;

    @Column(name = "video_key", nullable = false, length = 100)
    private String videoKey;

    @Column(nullable = false)
    private String url;

    @Column(length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "uploader_url")
    private String uploaderUrl;

    @Column(name = "uploader_key", length = 100)
    private String uploaderKey;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "is_official", nullable = false)
    private boolean isOfficial = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * Creates a new song PV row with required values.
     *
     * @param song parent song
     * @param service pv service provider
     * @param videoKey video key in service
     * @param url video url
     * @return created song pv
     */
    public static SongPv create(Song song, SongPvProvider service, String videoKey, String url) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (service == null) {
            throw new IllegalArgumentException("service is required");
        }
        if (videoKey == null || videoKey.isBlank()) {
            throw new IllegalArgumentException("videoKey is required");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }

        SongPv songPv = new SongPv();
        songPv.song = song;
        songPv.service = service;
        songPv.videoKey = videoKey;
        songPv.url = url;
        return songPv;
    }
}
