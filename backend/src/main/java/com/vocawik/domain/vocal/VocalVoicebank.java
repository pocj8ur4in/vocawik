package com.vocawik.domain.vocal;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Vocal voicebank detail entity using shared PK with {@link Resource}. */
@Getter
@Entity
@Table(name = "vocal_voicebanks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VocalVoicebank {

    @Id
    @Column(name = "id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vocal_character_id")
    private VocalCharacter vocalCharacter;

    @Column private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "links", columnDefinition = "jsonb")
    private JsonNode links;

    @Enumerated(EnumType.STRING)
    @Column(name = "voicebank_typ", nullable = false, length = 20)
    private VoicebankType voicebankType = VoicebankType.OTHER;

    /**
     * Creates a new vocal voicebank detail.
     *
     * @param canonicalName representative display name
     * @param thumbnailUrl representative thumbnail url (nullable)
     * @param content voicebank description (nullable)
     * @param links external links payload (nullable JSON array)
     * @param vocalCharacter parent vocal character (nullable)
     * @param voicebankType voicebank type (nullable, defaults to OTHER)
     * @return created vocal voicebank
     */
    public static VocalVoicebank create(
            String canonicalName,
            String thumbnailUrl,
            String content,
            JsonNode links,
            VocalCharacter vocalCharacter,
            VoicebankType voicebankType) {
        VocalVoicebank vocalVoicebank = new VocalVoicebank();
        vocalVoicebank.resource =
                Resource.create(ResourceType.VOICEBANK, canonicalName, thumbnailUrl);
        vocalVoicebank.content = content;
        vocalVoicebank.links = links;
        vocalVoicebank.vocalCharacter = vocalCharacter;
        vocalVoicebank.voicebankType = voicebankType == null ? VoicebankType.OTHER : voicebankType;
        return vocalVoicebank;
    }
}
