package com.vocawik.repository.resource;

import com.vocawik.domain.resource.ResourceStatus;

/**
 * Search condition for resource listing.
 *
 * @param status optional resource status filter
 * @param query optional canonical-name keyword
 * @param includeDeleted whether soft-deleted rows should be included
 */
public record ResourceCriteria(ResourceStatus status, String query, boolean includeDeleted) {}
