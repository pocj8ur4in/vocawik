package com.vocawik.repository.playlist;

import com.vocawik.domain.resource.ResourceStatus;

/** Search criteria for playlist listing. */
public record PlaylistCriteria(ResourceStatus status, String query, boolean includeDeleted) {}
