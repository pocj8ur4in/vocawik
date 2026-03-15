package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link VocalLink} persistence access. */
public interface VocalLinkRepository extends JpaRepository<VocalLink, Long> {

    /** Finds all links by vocal id in insertion order. */
    List<VocalLink> findAllByVocalIdOrderByIdAsc(Long vocalId);

    /** Deletes all links by vocal id. */
    void deleteByVocalId(Long vocalId);
}
