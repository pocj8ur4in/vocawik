package com.vocawik.controller;

import com.vocawik.aop.RateLimit;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.artist.ArtistCreateRequest;
import com.vocawik.dto.artist.ArtistListResponse;
import com.vocawik.dto.artist.ArtistSuggestionListResponse;
import com.vocawik.dto.artist.ArtistUpdateRequest;
import com.vocawik.dto.resource.ArtistResourceDetailResponse;
import com.vocawik.security.guest.AllowGuest;
import com.vocawik.service.artist.ArtistService;
import com.vocawik.service.captcha.CaptchaVerificationService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for Artist. */
@RestController
@Tag(name = "Artist", description = "Artist endpoints")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ArtistService is a Spring-managed bean reference and is not exposed externally.")
public class ArtistController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Map<String, String> ALLOWED_SORT_PROPERTIES =
            Map.of(
                    "updatedAt", "resource.updatedAt",
                    "createdAt", "resource.createdAt",
                    "name", "resource.canonicalName",
                    "match", "match");

    private final ArtistService artistService;
    private final ResourceService resourceService;
    private final CaptchaVerificationService captchaVerificationService;

    /**
     * Creates a new artist resource.
     *
     * @param request artist create payload
     * @return created artist resource detail
     */
    @PostMapping("/artists")
    @AllowGuest
    @Operation(summary = "Create artist", description = "Creates an artist.")
    public ResponseEntity<ArtistResourceDetailResponse> createArtist(
            @Valid @RequestBody ArtistCreateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        UUID resourceUuid = artistService.create(request);
        ArtistResourceDetailResponse detail = resourceService.getArtistByResourceUuid(resourceUuid);
        return ResponseEntity.created(URI.create("/artists/" + resourceUuid)).body(detail);
    }

    /** Gets an artist detail. */
    @GetMapping("/artists/{resourceUuid}")
    @Operation(summary = "Get artist", description = "Returns artist detail.")
    public ResponseEntity<ArtistResourceDetailResponse> getArtist(@PathVariable UUID resourceUuid) {
        return ResponseEntity.ok(resourceService.getArtistByResourceUuidWithTracking(resourceUuid));
    }

    /**
     * Updates an existing artist resource.
     *
     * @param resourceUuid artist resource UUID
     * @param request artist update payload
     * @return updated artist resource detail
     */
    @PatchMapping("/artists/{resourceUuid}")
    @AllowGuest
    @Operation(summary = "Update artist", description = "Updates an artist.")
    public ResponseEntity<ArtistResourceDetailResponse> updateArtist(
            @PathVariable UUID resourceUuid,
            @Valid @RequestBody ArtistUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        UUID updatedResourceUuid = artistService.update(resourceUuid, request);
        ArtistResourceDetailResponse detail =
                resourceService.getArtistByResourceUuid(updatedResourceUuid);
        return ResponseEntity.ok(detail);
    }

    /** Soft-deletes an artist. */
    @DeleteMapping("/artists/{resourceUuid}")
    @Operation(summary = "Delete artist", description = "Soft-deletes an artist.")
    public ResponseEntity<ArtistResourceDetailResponse> deleteArtist(
            @PathVariable UUID resourceUuid) {
        artistService.delete(resourceUuid);
        return ResponseEntity.ok(resourceService.getArtistByResourceUuid(resourceUuid));
    }

    /**
     * Searches artists with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional canonical name keyword
     * @param songUuids optional song resource UUID filter
     * @param groupArtistUuids optional group artist resource UUID filter
     * @param memberArtistUuids optional member artist resource UUID filter
     * @param pageable page and sort options
     * @return paged artist summaries
     */
    @GetMapping("/artists")
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Search artists",
            description = "Returns active artists with optional filters.")
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
    public ResponseEntity<ArtistListResponse> searchArtists(
            @Parameter(description = "Resource status filter")
                    @RequestParam(name = "status", required = false)
                    ResourceStatus status,
            @Parameter(description = "Canonical name keyword")
                    @RequestParam(name = "query", required = false)
                    String query,
            @Parameter(description = "Song resource UUID filter")
                    @RequestParam(name = "songUuids", required = false)
                    List<UUID> songUuids,
            @Parameter(description = "Group artist resource UUID filter")
                    @RequestParam(name = "groupArtistUuids", required = false)
                    List<UUID> groupArtistUuids,
            @Parameter(description = "Member artist resource UUID filter")
                    @RequestParam(name = "memberArtistUuids", required = false)
                    List<UUID> memberArtistUuids,
            @Parameter(hidden = true)
                    @PageableDefault(
                            size = 20,
                            sort = DEFAULT_SORT_PROPERTY,
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                artistService.search(
                        status,
                        query,
                        songUuids,
                        groupArtistUuids,
                        memberArtistUuids,
                        PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                sanitizeSort(pageable.getSort(), query))));
    }

    /** Suggests artists matching the current query. */
    @GetMapping("/artists/suggestions")
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Suggest artists",
            description = "Returns up to 10 artist suggestions matching the current query.")
    public ResponseEntity<ArtistSuggestionListResponse> suggestArtists(
            @Parameter(description = "Suggestion query") @RequestParam(name = "query") String query,
            @RequestHeader(name = "X-Captcha-Token", required = false) String captchaToken,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(captchaToken, httpServletRequest);
        return ResponseEntity.ok(artistService.suggest(query));
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
