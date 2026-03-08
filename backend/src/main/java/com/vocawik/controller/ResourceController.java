package com.vocawik.controller;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.resource.ArtistResourceDetailResponse;
import com.vocawik.dto.resource.PlaylistResourceDetailResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.dto.resource.SongResourceDetailResponse;
import com.vocawik.dto.resource.VocalResourceDetailResponse;
import com.vocawik.dto.resource.VoicebankResourceDetailResponse;
import com.vocawik.service.resource.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    /**
     * Gets denormalized resource detail from resource data.
     *
     * @param resourceUuid resource UUID
     * @return resource detail payload
     */
    @GetMapping("/resources/{resourceUuid}")
    @Operation(summary = "Get resource detail", description = "Returns resource detail.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Resource detail by type",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        oneOf = {
                                                            SongResourceDetailResponse.class,
                                                            ArtistResourceDetailResponse.class,
                                                            VocalResourceDetailResponse.class,
                                                            VoicebankResourceDetailResponse.class,
                                                            PlaylistResourceDetailResponse.class
                                                        })))
            })
    public ResponseEntity<Object> getResource(
            @Parameter(description = "Resource UUID") @PathVariable("resourceUuid")
                    UUID resourceUuid) {
        return ResponseEntity.ok(resourceService.getByResourceUuid(resourceUuid));
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
