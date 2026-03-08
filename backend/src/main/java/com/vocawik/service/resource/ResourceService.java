package com.vocawik.service.resource;

import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.resource.ResourceElementResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.repository.resource.ResourceCriteria;
import com.vocawik.repository.resource.ResourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for searching resources. */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    /**
     * Searches active resources with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional canonical-name query
     * @param pageable page/sort options
     * @return paged resource list response
     */
    @Transactional(readOnly = true)
    public ResourceListResponse search(ResourceStatus status, String query, Pageable pageable) {
        String normalizedQuery = normalizeQuery(query);
        Slice<Resource> resultSlice =
                resourceRepository.search(new ResourceCriteria(status, normalizedQuery), pageable);

        List<ResourceElementResponse> items =
                resultSlice.getContent().stream().map(this::toSummary).toList();

        return new ResourceListResponse(
                items, resultSlice.getNumber(), resultSlice.getSize(), resultSlice.hasNext());
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String trimmed = query.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResourceElementResponse toSummary(Resource resource) {
        return new ResourceElementResponse(
                resource.getUuid(),
                resource.getCanonicalName(),
                resource.getResourceType().name(),
                resource.getStatus().name(),
                resource.getViewCount(),
                resource.getThumbnailUrl(),
                resource.getCreatedAt(),
                resource.getUpdatedAt());
    }
}
