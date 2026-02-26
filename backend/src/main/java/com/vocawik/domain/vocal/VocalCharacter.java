package com.vocawik.domain.vocal;

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

/** Vocal character detail entity using shared PK with {@link Resource}. */
@Getter
@Entity
@Table(name = "vocal_characters")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VocalCharacter {

    @Id
    @Column(name = "id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Resource resource;

    @Column private String content;

    /**
     * Creates a new vocal character detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content vocal character description (nullable)
     * @return created vocal character
     */
    public static VocalCharacter create(String canonicalName, String thumbnailUrl, String content) {
        VocalCharacter vocalCharacter = new VocalCharacter();
        vocalCharacter.resource =
                Resource.create(ResourceType.CHARACTER, canonicalName, thumbnailUrl);
        vocalCharacter.content = content;
        return vocalCharacter;
    }
}
