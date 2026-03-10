package com.vocawik.controller;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.playlist.PlaylistCreateRequest;
import com.vocawik.dto.playlist.PlaylistListResponse;
import com.vocawik.dto.playlist.PlaylistUpdateRequest;
import com.vocawik.dto.resource.PlaylistResourceDetailResponse;
import com.vocawik.service.playlist.PlaylistService;
import com.vocawik.service.resource.ResourceService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
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

/** Endpoints for Playlist. */
@RestController
@Tag(name = "Playlist", description = "Playlist endpoints")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "Spring injects singleton services; references are not exposed outside controller boundaries.")
public class PlaylistController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Map<String, String> ALLOWED_SORT_PROPERTIES =
            Map.of(
                    "updatedAt", "resource.updatedAt",
                    "createdAt", "resource.createdAt");

    private final PlaylistService playlistService;
    private final ResourceService resourceService;

    /** Creates a new playlist resource. */
    @PostMapping("/playlists")
    @Operation(summary = "Create playlist", description = "Creates a playlist.")
    public ResponseEntity<PlaylistResourceDetailResponse> createPlaylist(
            @Valid @RequestBody PlaylistCreateRequest request) {
        UUID resourceUuid = playlistService.create(request);
        return ResponseEntity.created(java.net.URI.create("/playlists/" + resourceUuid))
                .body(resourceService.getPlaylistByResourceUuid(resourceUuid));
    }

    /** Updates a playlist resource. */
    @PatchMapping("/playlists/{resourceUuid}")
    @Operation(summary = "Update playlist", description = "Updates a playlist.")
    public ResponseEntity<PlaylistResourceDetailResponse> updatePlaylist(
            @PathVariable UUID resourceUuid, @Valid @RequestBody PlaylistUpdateRequest request) {
        UUID updatedResourceUuid = playlistService.update(resourceUuid, request);
        return ResponseEntity.ok(resourceService.getPlaylistByResourceUuid(updatedResourceUuid));
    }

    /** Soft-deletes a playlist resource. */
    @DeleteMapping("/playlists/{resourceUuid}")
    @Operation(summary = "Delete playlist", description = "Soft-deletes a playlist.")
    public ResponseEntity<PlaylistResourceDetailResponse> deletePlaylist(
            @PathVariable UUID resourceUuid) {
        playlistService.delete(resourceUuid);
        return ResponseEntity.ok(resourceService.getPlaylistByResourceUuid(resourceUuid));
    }

    /** Gets a playlist detail. */
    @GetMapping("/playlists/{resourceUuid}")
    @Operation(summary = "Get playlist", description = "Returns playlist detail.")
    public ResponseEntity<PlaylistResourceDetailResponse> getPlaylist(
            @PathVariable UUID resourceUuid) {
        return ResponseEntity.ok(resourceService.getPlaylistByResourceUuid(resourceUuid));
    }

    /** Searches playlists with optional filters. */
    @GetMapping("/playlists")
    @Operation(
            summary = "Search playlists",
            description = "Returns active playlists with optional filters.")
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
    public ResponseEntity<PlaylistListResponse> searchPlaylists(
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
                playlistService.search(
                        status,
                        query,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                sanitizeSort(pageable.getSort()))));
    }

    private Sort sanitizeSort(Sort requestedSort) {
        Sort sort =
                (requestedSort == null || requestedSort.isUnsorted())
                        ? Sort.by(DEFAULT_SORT_DIRECTION, DEFAULT_SORT_PROPERTY)
                        : requestedSort;

        ArrayList<Sort.Order> allowedOrders = new ArrayList<>();
        for (Sort.Order order : sort) {
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
