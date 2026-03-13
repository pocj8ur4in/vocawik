package com.vocawik.controller;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.history.RecentChangeListResponse;
import com.vocawik.dto.history.ResourceHistoryDetailResponse;
import com.vocawik.dto.resource.PopularResourceListResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.service.resource.ResourcePopularityService;
import com.vocawik.service.resource.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for Resource. */
@RestController
@Tag(name = "Resource", description = "Resource endpoints")
@RequiredArgsConstructor
public class ResourceController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("updatedAt", "createdAt");

    private final ResourceService resourceService;
    private final ResourceHistoryService resourceHistoryService;
    private final ResourcePopularityService resourcePopularityService;

    /**
     * Searches active resources filtered by status/query.
     *
     * @param status optional resource status filter
     * @param query optional canonical name query
     * @param pageable page and sort options
     * @return paged resource summaries
     */
    @GetMapping("/resources")
    @Operation(
            summary = "Search resources",
            description = "Returns active resources with optional filters.")
    @Parameters({
        @Parameter(
                name = "page",
                in = ParameterIn.QUERY,
                description = "Page index",
                example = "0",
                schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
        @Parameter(
                name = "size",
                in = ParameterIn.QUERY,
                description = "Page size",
                example = "20",
                schema = @Schema(type = "integer", defaultValue = "20", minimum = "1")),
        @Parameter(
                name = "sort",
                in = ParameterIn.QUERY,
                description = "Sort (format: {property},{asc|desc})",
                example = "updatedAt,desc",
                schema =
                        @Schema(
                                type = "string",
                                defaultValue = "updatedAt,desc",
                                allowableValues = {
                                    "updatedAt,asc",
                                    "updatedAt,desc",
                                    "createdAt,asc",
                                    "createdAt,desc"
                                }))
    })
    public ResponseEntity<ResourceListResponse> searchResources(
            @Parameter(description = "Resource status filter")
                    @RequestParam(name = "status", required = false)
                    ResourceStatus status,
            @Parameter(description = "Canonical name keyword")
                    @RequestParam(name = "query", required = false)
                    String query,
            @Parameter(hidden = true)
                    @PageableDefault(
                            size = 20,
                            sort = DEFAULT_SORT_PROPERTY,
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                resourceService.search(
                        status,
                        query,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                sanitizeSort(pageable.getSort()))));
    }

    /** Lists the most recent resource changes across all resource types. */
    @GetMapping("/histories/recent")
    @Operation(
            summary = "List recent changes",
            description = "Returns the most recent resource changes across all resource types.")
    @Parameters({
        @Parameter(
                name = "size",
                in = ParameterIn.QUERY,
                description = "Maximum number of changes to return (capped at 10)",
                example = "10",
                schema = @Schema(type = "integer", defaultValue = "10", minimum = "1"))
    })
    public ResponseEntity<RecentChangeListResponse> listRecentChanges(
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(resourceHistoryService.listRecentChanges(size));
    }

    /** Lists popular resources over the last 10 minutes. */
    @GetMapping("/resources/popular")
    @Operation(
            summary = "List popular resources",
            description = "Returns the most viewed public resources during the last 10 minutes.")
    @Parameters({
        @Parameter(
                name = "size",
                in = ParameterIn.QUERY,
                description = "Maximum number of items to return (capped at 10)",
                example = "10",
                schema = @Schema(type = "integer", defaultValue = "10", minimum = "1"))
    })
    public ResponseEntity<PopularResourceListResponse> listPopularResources(
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(resourcePopularityService.listPopularResources(size));
    }

    /** Gets a single resource history revision. */
    @GetMapping("/resources/{resourceUuid}/histories/{historyUuid}")
    @Operation(
            summary = "Get resource history",
            description =
                    "Returns revision metadata and snapshot data for a single resource history row.")
    public ResponseEntity<ResourceHistoryDetailResponse> getResourceHistory(
            @PathVariable UUID resourceUuid, @PathVariable UUID historyUuid) {
        return ResponseEntity.ok(
                resourceHistoryService.getByResourceUuidAndHistoryUuid(resourceUuid, historyUuid));
    }

    private Sort sanitizeSort(Sort requestedSort) {
        Sort sort =
                (requestedSort == null || requestedSort.isUnsorted())
                        ? Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_PROPERTY)
                        : requestedSort;

        ArrayList<Sort.Order> allowedOrders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "Unsupported sort property: " + order.getProperty());
            }
            allowedOrders.add(order);
        }
        return Sort.by(allowedOrders);
    }
}
