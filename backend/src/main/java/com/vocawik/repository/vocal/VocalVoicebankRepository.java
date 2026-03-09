package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalVoicebank;
import com.vocawik.repository.common.ResourceRefProjection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link VocalVoicebank} persistence access. */
public interface VocalVoicebankRepository
        extends JpaRepository<VocalVoicebank, Long>, VoicebankCriteriaRepository {

    /**
     * Finds active voicebanks by resource UUIDs.
     *
     * @param resourceUuids voicebank resource UUIDs
     * @return matching active voicebanks
     */
    @Query(
            """
            select vb.id as id, vb.resource.uuid as resourceUuid
            from VocalVoicebank vb
            where vb.resource.isDeleted = false
              and vb.resource.uuid in :resourceUuids
            """)
    List<ResourceRefProjection> findResourceRefsByResourceUuids(
            @Param("resourceUuids") Collection<java.util.UUID> resourceUuids);
}
