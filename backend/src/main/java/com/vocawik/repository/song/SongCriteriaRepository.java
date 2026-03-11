package com.vocawik.repository.song;

import com.vocawik.domain.song.Song;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom search repository for {@link Song}. */
public interface SongCriteriaRepository {

    /**
     * Searches songs with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return paged songs
     */
    Page<Song> search(SongCriteria criteria, Pageable pageable);
}
