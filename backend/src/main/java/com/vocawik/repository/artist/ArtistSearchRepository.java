package com.vocawik.repository.artist;

import com.vocawik.domain.artist.Artist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link Artist}. */
public interface ArtistSearchRepository {

    /**
     * Searches artists with optional filters.
     *
     * @param condition search condition
     * @param pageable page/sort options
     * @return sliced artists
     */
    Slice<Artist> search(ArtistSearchCondition condition, Pageable pageable);
}
