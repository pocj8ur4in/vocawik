package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.Vocal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom search repository for {@link Vocal}. */
public interface VocalCriteriaRepository {

    /**
     * Searches vocals with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return paged vocals
     */
    Page<Vocal> search(VocalCriteria criteria, Pageable pageable);
}
