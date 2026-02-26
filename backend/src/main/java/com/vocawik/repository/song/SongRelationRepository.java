package com.vocawik.repository.song;

import com.vocawik.domain.song.SongRelation;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link SongRelation} persistence access. */
public interface SongRelationRepository extends JpaRepository<SongRelation, Long> {}
