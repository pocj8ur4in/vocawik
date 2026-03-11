package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Custom search repository for {@link Resource}. */
public interface ResourceCriteriaRepository {

    /**
     * Searches resources with optional filters.
     *
     * @param criteria search criteria
     * @param pageable page/sort options
     * @return paged resources
     */
    Page<Resource> search(ResourceCriteria criteria, Pageable pageable);
}
