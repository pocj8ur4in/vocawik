package com.vocawik.repository.artist;

import com.vocawik.domain.artist.Artist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom search repository for {@link Artist}. */
public interface ArtistCriteriaRepository {

    /**
     * Searches artists with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return paged artists
     */
    Page<Artist> search(ArtistCriteria criteria, Pageable pageable);
}
