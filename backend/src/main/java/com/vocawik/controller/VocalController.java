package com.vocawik.controller;

import com.vocawik.aop.RateLimit;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.resource.VocalResourceDetailResponse;
import com.vocawik.dto.vocal.VocalCreateRequest;
import com.vocawik.dto.vocal.VocalListResponse;
import com.vocawik.dto.vocal.VocalSuggestionListResponse;
import com.vocawik.dto.vocal.VocalUpdateRequest;
import com.vocawik.security.guest.AllowGuest;
import com.vocawik.service.resource.ResourceService;
import com.vocawik.service.vocal.VocalService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for Vocal. */
@RestController
@Tag(name = "Vocal", description = "Vocal endpoints")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "VocalService is a Spring-managed bean reference and is not exposed externally.")
public class VocalController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Map<String, String> ALLOWED_SORT_PROPERTIES =
            Map.of(
                    "updatedAt", "resource.updatedAt",
                    "createdAt", "resource.createdAt",
                    "name", "resource.canonicalName",
                    "match", "match");

    private final VocalService vocalService;
    private final ResourceService resourceService;

    /**
     * Creates a new vocal resource.
     *
     * @param request vocal create payload
     * @return created vocal resource detail
     */
    @PostMapping("/vocals")
    @AllowGuest
    @Operation(summary = "Create vocal", description = "Creates a vocal.")
    public ResponseEntity<VocalResourceDetailResponse> createVocal(
            @Valid @RequestBody VocalCreateRequest request) {
        UUID resourceUuid = vocalService.create(request);
        VocalResourceDetailResponse detail = resourceService.getVocalByResourceUuid(resourceUuid);
        return ResponseEntity.created(URI.create("/vocals/" + resourceUuid)).body(detail);
    }

    /** Gets a vocal detail. */
    @GetMapping("/vocals/{resourceUuid}")
    @Operation(summary = "Get vocal", description = "Returns vocal detail.")
    public ResponseEntity<VocalResourceDetailResponse> getVocal(@PathVariable UUID resourceUuid) {
        return ResponseEntity.ok(resourceService.getVocalByResourceUuidWithTracking(resourceUuid));
    }

    /**
     * Updates an existing vocal resource.
     *
     * @param resourceUuid vocal resource UUID
     * @param request vocal update payload
     * @return updated vocal resource detail
     */
    @PatchMapping("/vocals/{resourceUuid}")
    @AllowGuest
    @Operation(summary = "Update vocal", description = "Updates a vocal.")
    public ResponseEntity<VocalResourceDetailResponse> updateVocal(
            @PathVariable UUID resourceUuid, @Valid @RequestBody VocalUpdateRequest request) {
        UUID updatedResourceUuid = vocalService.update(resourceUuid, request);
        VocalResourceDetailResponse detail =
                resourceService.getVocalByResourceUuid(updatedResourceUuid);
        return ResponseEntity.ok(detail);
    }

    /** Soft-deletes a vocal. */
    @DeleteMapping("/vocals/{resourceUuid}")
    @Operation(summary = "Delete vocal", description = "Soft-deletes a vocal.")
    public ResponseEntity<VocalResourceDetailResponse> deleteVocal(
            @PathVariable UUID resourceUuid) {
        vocalService.delete(resourceUuid);
        return ResponseEntity.ok(resourceService.getVocalByResourceUuid(resourceUuid));
    }

    /**
     * Searches vocals with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional name keyword
     * @param songUuids optional song resource UUID filter
     * @param pageable page and sort options
     * @return paged vocal summaries
     */
    @GetMapping("/vocals")
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Search vocal",
            description = "Returns active vocals with optional filters.")
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
                                    "createdAt,desc",
                                    "name,asc",
                                    "name,desc",
                                    "match,asc"
                                }))
    })
    public ResponseEntity<VocalListResponse> searchVocals(
            @Parameter(description = "Resource status filter")
                    @RequestParam(name = "status", required = false)
                    ResourceStatus status,
            @Parameter(description = "Name keyword") @RequestParam(name = "query", required = false)
                    String query,
            @Parameter(description = "Song resource UUID filter")
                    @RequestParam(name = "songUuids", required = false)
                    List<UUID> songUuids,
            @Parameter(hidden = true)
                    @PageableDefault(
                            size = 20,
                            sort = DEFAULT_SORT_PROPERTY,
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                vocalService.search(
                        status,
                        query,
                        songUuids,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                sanitizeSort(pageable.getSort(), query))));
    }

    /** Suggests vocals matching the current query. */
    @GetMapping("/vocals/suggestions")
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Suggest vocals",
            description = "Returns up to 10 vocal suggestions matching the current query.")
    public ResponseEntity<VocalSuggestionListResponse> suggestVocals(
            @Parameter(description = "Suggestion query") @RequestParam(name = "query")
                    String query) {
        return ResponseEntity.ok(vocalService.suggest(query));
    }

    private Sort sanitizeSort(Sort requestedSort, String query) {
        Sort sort =
                (requestedSort == null || requestedSort.isUnsorted())
                        ? Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_PROPERTY)
                        : requestedSort;
        boolean hasSearchQuery = query != null && !query.trim().isEmpty();

        ArrayList<Sort.Order> allowedOrders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if ("match".equals(order.getProperty()) && order.isDescending()) {
                throw new IllegalArgumentException("match sort only supports ascending order");
            }
            if ("match".equals(order.getProperty()) && !hasSearchQuery) {
                allowedOrders.add(
                        new Sort.Order(
                                DEFAULT_SORT_DIRECTION,
                                ALLOWED_SORT_PROPERTIES.get(DEFAULT_SORT_PROPERTY)));
                continue;
            }
            String internalProperty = ALLOWED_SORT_PROPERTIES.get(order.getProperty());
            if (internalProperty == null) {
                throw new IllegalArgumentException(
                        "Unsupported sort property: " + order.getProperty());
            }
            allowedOrders.add(new Sort.Order(order.getDirection(), internalProperty));
        }
        return Sort.by(allowedOrders);
    }
}
