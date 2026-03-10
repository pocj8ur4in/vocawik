package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.Vocal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link Vocal}. */
public interface VocalCriteriaRepository {

    /**
     * Searches vocals with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return sliced vocals
     */
    Slice<Vocal> search(VocalCriteria criteria, Pageable pageable);
}
