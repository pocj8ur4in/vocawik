package com.vocawik.repository.song;

import com.vocawik.domain.song.SongVoicebank;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for {@link SongVoicebank} persistence access. */
public interface SongVoicebankRepository extends JpaRepository<SongVoicebank, Long> {

    /** Returns whether any song-voicebank row references the voicebank id. */
    boolean existsByVoicebankId(Long voicebankId);

    /** Finds all song-voicebank rows by song id in display order. */
    List<SongVoicebank> findAllBySongIdOrderBySortOrderAscIdAsc(Long songId);

    /** Finds all song-voicebank rows by voicebank id in display order. */
    List<SongVoicebank> findAllByVoicebankIdOrderBySortOrderAscIdAsc(Long voicebankId);

    /** Deletes all song-voicebank rows by song id. */
    @Modifying(flushAutomatically = true)
    @Query("delete from SongVoicebank svb where svb.song.id = :songId")
    void deleteBySongId(@Param("songId") Long songId);
}
