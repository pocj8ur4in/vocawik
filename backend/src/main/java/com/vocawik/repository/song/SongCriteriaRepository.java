package com.vocawik.repository.song;

import com.vocawik.domain.song.Song;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

    /**
     * Searches all songs matching the given filters without pagination.
     *
     * @param criteria search criteria
     * @param sort sort options
     * @return ordered songs
     */
    List<Song> searchAll(SongCriteria criteria, Sort sort);

    /**
     * Searches a playback slice using cursor pagination.
     *
     * @param criteria search criteria
     * @param cursor cursor condition for the next slice
     * @param sortOrder primary sort order
     * @param limit max rows to load
     * @return ordered slice rows
     */
    List<Song> searchPlaybackSlice(
            SongCriteria criteria,
            SongPlaybackCursorCriteria cursor,
            Sort.Order sortOrder,
            int limit);

    /**
     * Counts songs matching the given criteria.
     *
     * @param criteria search criteria
     * @return total matching rows
     */
    long count(SongCriteria criteria);
}
