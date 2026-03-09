package com.vocawik.domain.song;

import com.fasterxml.jackson.databind.JsonNode;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Song detail entity using shared PK with {@link Resource}. */
@Getter
@Entity
@Table(name = "songs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song {

    @Id
    @Column(name = "id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Resource resource;

    @Column private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "links", columnDefinition = "jsonb")
    private JsonNode links;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "song_type", nullable = false, length = 20)
    private SongType songType = SongType.OTHER;

    /**
     * Creates a new song detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content song description (nullable)
     * @param links external links payload (nullable JSON array)
     * @param publishedAt published datetime (nullable)
     * @param songType classification type (nullable, defaults to OTHER)
     * @return created song
     */
    public static Song create(
            String canonicalName,
            String thumbnailUrl,
            String content,
            JsonNode links,
            LocalDateTime publishedAt,
            SongType songType) {
        Song song = new Song();
        song.resource = Resource.create(ResourceType.SONG, canonicalName, thumbnailUrl);
        song.content = content;
        song.links = links;
        song.publishedAt = publishedAt;
        song.songType = songType == null ? SongType.OTHER : songType;
        return song;
    }

    /**
     * Updates song detail fields.
     *
     * @param content updated content
     * @param links updated links
     * @param publishedAt updated published datetime
     * @param songType updated song type
     */
    public void update(
            String content, JsonNode links, LocalDateTime publishedAt, SongType songType) {
        this.content = content;
        this.links = links;
        this.publishedAt = publishedAt;
        this.songType = songType == null ? SongType.OTHER : songType;
    }
}
