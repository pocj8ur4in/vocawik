package com.vocawik.domain.playlist;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Playlist detail entity using shared PK with {@link Resource}. */
@Getter
@Entity
@Table(name = "playlists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist {

    @Id
    @Column(name = "id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Resource resource;

    @Column private String content;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "is_system_managed", nullable = false)
    private boolean systemManaged = false;

    /**
     * Creates a new playlist detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content playlist description (nullable)
     * @param isPublic visibility flag
     * @return created playlist
     */
    public static Playlist create(
            String canonicalName, String thumbnailUrl, String content, boolean isPublic) {
        Playlist playlist = new Playlist();
        playlist.resource = Resource.create(ResourceType.PLAYLIST, canonicalName, thumbnailUrl);
        playlist.content = content;
        playlist.isPublic = isPublic;
        return playlist;
    }

    /** Updates playlist-owned fields. */
    public void update(String content, Boolean isPublic) {
        this.content = content;
        if (isPublic != null) {
            this.isPublic = isPublic;
        }
    }
}
