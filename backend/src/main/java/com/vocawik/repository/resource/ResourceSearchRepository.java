package com.vocawik.repository.resource;

import com.vocawik.domain.resource.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** Custom search repository for {@link Resource}. */
public interface ResourceSearchRepository {

    /**
     * Searches resources with optional filters.
     *
     * @param condition search condition
     * @param pageable page/sort options
     * @return sliced resources
     */
    Slice<Resource> search(ResourceSearchCondition condition, Pageable pageable);
}
