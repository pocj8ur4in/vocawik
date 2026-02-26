package com.vocawik.domain.artist;

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

/** Artist detail entity using shared PK with {@link Resource}. */
@Getter
@Entity
@Table(name = "artists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Artist {

    @Id
    @Column(name = "id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Resource resource;

    @Column private String content;

    /**
     * Creates a new artist detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content artist description (nullable)
     * @return created artist
     */
    public static Artist create(String canonicalName, String thumbnailUrl, String content) {
        Artist artist = new Artist();
        artist.resource = Resource.create(ResourceType.ARTIST, canonicalName, thumbnailUrl);
        artist.content = content;
        return artist;
    }
}
