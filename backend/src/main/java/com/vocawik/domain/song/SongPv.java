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

    @Column(length = 255)
    private String title;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

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
     * @return created song pv
     */
    public static SongPv create(Song song, SongPvProvider service, String videoKey) {
        return create(song, service, videoKey, null, null, null, null, true, null, 0);
    }

    /**
     * Creates a new song PV row.
     *
     * @param song parent song
     * @param service pv service provider
     * @param videoKey video key in service
     * @param title pv title
     * @param thumbnailUrl pv thumbnail url
     * @param uploaderKey uploader key
     * @param durationSeconds duration in seconds
     * @param isOfficial whether official upload
     * @param publishedAt published datetime
     * @param sortOrder display order
     * @return created song pv
     */
    public static SongPv create(
            Song song,
            SongPvProvider service,
            String videoKey,
            String title,
            String thumbnailUrl,
            String uploaderKey,
            Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            int sortOrder) {
        if (song == null) {
            throw new IllegalArgumentException("song is required");
        }
        if (service == null) {
            throw new IllegalArgumentException("service is required");
        }
        if (videoKey == null || videoKey.isBlank()) {
            throw new IllegalArgumentException("videoKey is required");
        }
        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must be >= 0");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }

        SongPv songPv = new SongPv();
        songPv.song = song;
        songPv.service = service;
        songPv.videoKey = videoKey.trim();
        songPv.title = title;
        songPv.thumbnailUrl = thumbnailUrl;
        songPv.uploaderKey = uploaderKey;
        songPv.durationSeconds = durationSeconds;
        songPv.isOfficial = isOfficial;
        songPv.publishedAt = publishedAt;
        songPv.sortOrder = sortOrder;
        return songPv;
    }

    /**
     * Updates mutable PV metadata fields.
     *
     * @param title updated title
     * @param thumbnailUrl updated thumbnail URL
     * @param uploaderKey updated uploader key
     * @param durationSeconds updated duration in seconds
     * @param isOfficial updated official flag
     * @param publishedAt updated published datetime
     * @param sortOrder updated sort order
     */
    public void updateMetadata(
            String title,
            String thumbnailUrl,
            String uploaderKey,
            Integer durationSeconds,
            boolean isOfficial,
            LocalDateTime publishedAt,
            int sortOrder) {
        if (durationSeconds != null && durationSeconds < 0) {
            throw new IllegalArgumentException("durationSeconds must be >= 0");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder must be >= 0");
        }
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
        this.uploaderKey = uploaderKey;
        this.durationSeconds = durationSeconds;
        this.isOfficial = isOfficial;
        this.publishedAt = publishedAt;
        this.sortOrder = sortOrder;
    }
}
