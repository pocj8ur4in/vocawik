package com.vocawik.repository.song;

import com.vocawik.domain.song.Song;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link Song}. */
public interface SongSearchRepository {

    /**
     * Searches songs with optional filters.
     *
     * @param condition search condition
     * @param pageable page/sort options
     * @return sliced songs
     */
    Slice<Song> search(SongSearchCondition condition, Pageable pageable);
}
