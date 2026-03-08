package com.vocawik.repository.vocal;

import com.vocawik.domain.vocal.VocalCharacter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link VocalCharacter}. */
public interface VocalCriteriaRepository {

    /**
     * Searches vocal characters with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return sliced vocal characters
     */
    Slice<VocalCharacter> search(VocalCriteria criteria, Pageable pageable);
}
