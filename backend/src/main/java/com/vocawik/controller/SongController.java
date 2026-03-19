package com.vocawik.controller;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.SongType;
import com.vocawik.dto.resource.SongResourceDetailResponse;
import com.vocawik.dto.song.SongCreateRequest;
import com.vocawik.dto.song.SongListResponse;
import com.vocawik.dto.song.SongPvResolveRequest;
import com.vocawik.dto.song.SongPvResolveResponse;
import com.vocawik.dto.song.SongSuggestionListResponse;
import com.vocawik.dto.song.SongUpdateRequest;
import com.vocawik.service.resource.ResourceService;
import com.vocawik.service.song.SongService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for Song. */
@RestController
@Tag(name = "Song", description = "Song endpoints")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "SongService is a Spring-managed bean reference and is not exposed externally.")
public class SongController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Map<String, String> ALLOWED_SORT_PROPERTIES =
            Map.of(
                    "updatedAt", "resource.updatedAt",
                    "createdAt", "resource.createdAt",
                    "publishedAt", "publishedAt",
                    "name", "resource.canonicalName",
                    "match", "match");

    private final SongService songService;
    private final ResourceService resourceService;

    /**
     * Creates a new song resource.
     *
     * @param request song create payload
     * @return created song resource detail
     */
    @PostMapping("/songs")
    @Operation(summary = "Create song", description = "Creates a song.")
    public ResponseEntity<SongResourceDetailResponse> createSong(
            @Valid @RequestBody SongCreateRequest request) {
        UUID resourceUuid = songService.create(request);
        SongResourceDetailResponse detail = resourceService.getSongByResourceUuid(resourceUuid);
        return ResponseEntity.created(URI.create("/songs/" + resourceUuid)).body(detail);
    }

    /** Gets a song detail. */
    @GetMapping("/songs/{resourceUuid}")
    @Operation(summary = "Get song", description = "Returns song detail.")
    public ResponseEntity<SongResourceDetailResponse> getSong(@PathVariable UUID resourceUuid) {
        return ResponseEntity.ok(resourceService.getSongByResourceUuidWithTracking(resourceUuid));
    }

    /**
     * Updates an existing song resource.
     *
     * @param resourceUuid song resource UUID
     * @param request song update payload
     * @return updated song resource detail
     */
    @PatchMapping("/songs/{resourceUuid}")
    @Operation(summary = "Update song", description = "Updates a song.")
    public ResponseEntity<SongResourceDetailResponse> updateSong(
            @PathVariable UUID resourceUuid, @Valid @RequestBody SongUpdateRequest request) {
        UUID updatedResourceUuid = songService.update(resourceUuid, request);
        SongResourceDetailResponse detail =
                resourceService.getSongByResourceUuid(updatedResourceUuid);
        return ResponseEntity.ok(detail);
    }

    /** Soft-deletes a song. */
    @DeleteMapping("/songs/{resourceUuid}")
    @Operation(summary = "Delete song", description = "Soft-deletes a song.")
    public ResponseEntity<SongResourceDetailResponse> deleteSong(@PathVariable UUID resourceUuid) {
        songService.delete(resourceUuid);
        return ResponseEntity.ok(resourceService.getSongByResourceUuid(resourceUuid));
    }

    /**
     * Searches songs with optional filters.
     *
     * @param status optional resource status filter
     * @param songTypes optional song type filters
     * @param query optional canonical name keyword
     * @param artistUuids optional artist resource UUID filter
     * @param vocalUuids optional vocal resource UUID filter
     * @param publishedFrom optional published-at start datetime (inclusive)
     * @param publishedTo optional published-at end datetime (inclusive)
     * @param pageable page and sort options
     * @return paged song summaries
     */
    @GetMapping("/songs")
    @Operation(
            summary = "Search songs",
            description = "Returns active songs with optional filters.")
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
                                    "publishedAt,asc",
                                    "publishedAt,desc",
                                    "name,asc",
                                    "name,desc",
                                    "match,asc"
                                }))
    })
    public ResponseEntity<SongListResponse> searchSongs(
            @Parameter(description = "Resource status filter")
                    @RequestParam(name = "status", required = false)
                    ResourceStatus status,
            @Parameter(description = "Song type filters")
                    @RequestParam(name = "songTypes", required = false)
                    List<SongType> songTypes,
            @Parameter(description = "Canonical name keyword")
                    @RequestParam(name = "query", required = false)
                    String query,
            @Parameter(description = "Artist resource UUID filter")
                    @RequestParam(name = "artistUuids", required = false)
                    List<UUID> artistUuids,
            @Parameter(description = "Vocal resource UUID filter")
                    @RequestParam(name = "vocalUuids", required = false)
                    List<UUID> vocalUuids,
            @Parameter(description = "Published-at start datetime, e.g. 2026-03-01T00:00:00")
                    @RequestParam(name = "publishedFrom", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime publishedFrom,
            @Parameter(description = "Published-at end datetime, e.g. 2026-03-31T23:59:59")
                    @RequestParam(name = "publishedTo", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime publishedTo,
            @Parameter(hidden = true)
                    @PageableDefault(
                            size = 20,
                            sort = DEFAULT_SORT_PROPERTY,
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        validatePublishedRange(publishedFrom, publishedTo);

        return ResponseEntity.ok(
                songService.search(
                        status,
                        songTypes,
                        query,
                        artistUuids,
                        vocalUuids,
                        publishedFrom,
                        publishedTo,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                sanitizeSort(pageable.getSort(), query))));
    }

    /** Suggests songs matching the current query. */
    @GetMapping("/songs/suggestions")
    @Operation(
            summary = "Suggest songs",
            description = "Returns up to 10 song suggestions matching the current query.")
    public ResponseEntity<SongSuggestionListResponse> suggestSongs(
            @Parameter(description = "Suggestion query") @RequestParam(name = "query")
                    String query) {
        return ResponseEntity.ok(songService.suggest(query));
    }

    /**
     * Resolves PV metadata from a PV URL.
     *
     * @param request pv resolve request
     * @return resolved pv metadata
     */
    @PostMapping("/songs/pvs")
    @Operation(
            summary = "Resolve song PV",
            description = "Returns parsed/normalized PV metadata from URL.")
    public ResponseEntity<SongPvResolveResponse> resolveSongPv(
            @Valid @RequestBody SongPvResolveRequest request) {
        return ResponseEntity.ok(songService.resolveSongPv(request));
    }

    private void validatePublishedRange(LocalDateTime publishedFrom, LocalDateTime publishedTo) {
        if (publishedFrom != null && publishedTo != null && publishedFrom.isAfter(publishedTo)) {
            throw new IllegalArgumentException(
                    "publishedFrom must be earlier than or equal to publishedTo");
        }
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
