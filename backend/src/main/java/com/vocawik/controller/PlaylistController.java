package com.vocawik.controller;

import com.vocawik.aop.RateLimit;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.playlist.PlaylistCreateRequest;
import com.vocawik.dto.playlist.PlaylistListResponse;
import com.vocawik.dto.playlist.PlaylistPlaybackResponse;
import com.vocawik.dto.playlist.PlaylistSongListResponse;
import com.vocawik.dto.playlist.PlaylistSuggestionListResponse;
import com.vocawik.dto.playlist.PlaylistUpdateRequest;
import com.vocawik.dto.resource.PlaylistResourceDetailResponse;
import com.vocawik.security.guest.AllowGuest;
import com.vocawik.service.captcha.CaptchaVerificationService;
import com.vocawik.service.playlist.PlaylistService;
import com.vocawik.service.resource.ResourceService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
                    "createdAt", "resource.createdAt",
                    "name", "resource.canonicalName",
                    "match", "match");

    private final PlaylistService playlistService;
    private final ResourceService resourceService;
    private final CaptchaVerificationService captchaVerificationService;

    /** Creates a new playlist resource. */
    @PostMapping("/playlists")
    @AllowGuest
    @Operation(summary = "Create playlist", description = "Creates a playlist.")
    public ResponseEntity<PlaylistResourceDetailResponse> createPlaylist(
            @Valid @RequestBody PlaylistCreateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        UUID resourceUuid = playlistService.create(request);
        return ResponseEntity.created(java.net.URI.create("/playlists/" + resourceUuid))
                .body(resourceService.getPlaylistByResourceUuid(resourceUuid));
    }

    /** Updates a playlist resource. */
    @PutMapping("/playlists/{resourceUuid}")
    @AllowGuest
    @Operation(summary = "Update playlist", description = "Updates a playlist.")
    public ResponseEntity<PlaylistResourceDetailResponse> updatePlaylist(
            @PathVariable UUID resourceUuid,
            @Valid @RequestBody PlaylistUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
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
        return ResponseEntity.ok(
                resourceService.getPlaylistByResourceUuidWithTracking(resourceUuid));
    }

    /** Gets a playlist playback payload. */
    @GetMapping("/playlists/{resourceUuid}/playback")
    @Operation(
            summary = "Get playlist playback",
            description = "Returns player-focused playlist payload with ordered song sources.")
    public ResponseEntity<PlaylistPlaybackResponse> getPlaylistPlayback(
            @PathVariable UUID resourceUuid,
            @Parameter(description = "Preferred PV service to prioritize for each song")
                    @RequestParam(name = "preferredPvService", required = false)
                    String preferredPvService) {
        return ResponseEntity.ok(playlistService.getPlayback(resourceUuid, preferredPvService));
    }

    /** Gets a cursor-paginated playlist song list. */
    @GetMapping("/playlists/{resourceUuid}/songs")
    @Operation(
            summary = "Get playlist songs",
            description = "Returns a cursor-paginated playlist song list.")
    public ResponseEntity<PlaylistSongListResponse> getPlaylistSongs(
            @PathVariable UUID resourceUuid,
            @Parameter(description = "Cursor returned from the previous playlist songs page")
                    @RequestParam(name = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Number of songs to return", example = "50")
                    @RequestParam(name = "limit", required = false)
                    Integer limit) {
        return ResponseEntity.ok(playlistService.getSongs(resourceUuid, cursor, limit));
    }

    /** Searches playlists with optional filters. */
    @GetMapping("/playlists")
    @RateLimit(requests = 60, seconds = 60)
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
                                    "createdAt,desc",
                                    "name,asc",
                                    "name,desc",
                                    "match,asc"
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
                                sanitizeSort(pageable.getSort(), query))));
    }

    /** Suggests playlists matching the current query. */
    @GetMapping("/playlists/suggestions")
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Suggest playlists",
            description = "Returns up to 10 playlist suggestions matching the current query.")
    public ResponseEntity<PlaylistSuggestionListResponse> suggestPlaylists(
            @Parameter(description = "Suggestion query") @RequestParam(name = "query")
                    String query) {
        return ResponseEntity.ok(playlistService.suggest(query));
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
