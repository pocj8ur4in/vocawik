package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link Resource}. */
public interface ResourceCriteriaRepository {

    /**
     * Searches resources with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return sliced resources
     */
    Slice<Resource> search(ResourceCriteria criteria, Pageable pageable);
}
