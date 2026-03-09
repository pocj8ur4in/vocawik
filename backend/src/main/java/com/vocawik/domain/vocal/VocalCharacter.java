package com.vocawik.domain.vocal;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "links", columnDefinition = "jsonb")
    private JsonNode links;

    /**
     * Creates a new vocal character detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content vocal character description (nullable)
     * @param links external links payload (nullable JSON array)
     * @return created vocal character
     */
    public static VocalCharacter create(
            String canonicalName, String thumbnailUrl, String content, JsonNode links) {
        VocalCharacter vocalCharacter = new VocalCharacter();
        vocalCharacter.resource = Resource.create(ResourceType.VOCAL, canonicalName, thumbnailUrl);
        vocalCharacter.content = content;
        vocalCharacter.links = links;
        return vocalCharacter;
    }

    /**
     * Updates vocal character detail fields.
     *
     * @param content updated description
     * @param links updated external links
     */
    public void update(String content, JsonNode links) {
        this.content = content;
        this.links = links;
    }
}
