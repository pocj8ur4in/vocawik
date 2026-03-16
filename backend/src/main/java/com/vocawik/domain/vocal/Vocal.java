package com.vocawik.domain.vocal;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Vocal detail entity using shared PK with {@link Resource}. */
@Getter
@Entity
@Table(name = "vocals")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vocal {

    @Id
    @Column(name = "id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Resource resource;

    @Column private String content;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "vocal")
    private List<VocalLink> vocalLinks = new ArrayList<>();

    /**
     * Creates a new vocal detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content vocal description (nullable)
     * @return created vocal
     */
    public static Vocal create(String canonicalName, String thumbnailUrl, String content) {
        Vocal vocal = new Vocal();
        vocal.resource = Resource.create(ResourceType.VOCAL, canonicalName, thumbnailUrl);
        vocal.content = content;
        return vocal;
    }

    /**
     * Updates vocal detail fields.
     *
     * @param content updated description
     */
    public void update(String content) {
        this.content = content;
    }
}
