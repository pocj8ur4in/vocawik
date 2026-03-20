package com.vocawik.controller;

import com.vocawik.aop.RateLimit;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.debate.DebateCommentCreateRequest;
import com.vocawik.dto.debate.DebateCommentResponse;
import com.vocawik.dto.debate.DebateCommentUpdateRequest;
import com.vocawik.dto.debate.DebateCreateRequest;
import com.vocawik.dto.debate.DebateDetailResponse;
import com.vocawik.dto.debate.DebateListElementResponse;
import com.vocawik.dto.debate.DebateListResponse;
import com.vocawik.dto.debate.DebateStatusResponse;
import com.vocawik.dto.debate.DebateStatusUpdateRequest;
import com.vocawik.dto.history.RecentChangeListResponse;
import com.vocawik.dto.history.ResourceHistoryDetailResponse;
import com.vocawik.dto.resource.PopularResourceListResponse;
import com.vocawik.dto.resource.ResourceInfoResponse;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.dto.resource.ResourceSuggestionListResponse;
import com.vocawik.security.guest.AllowGuest;
import com.vocawik.service.captcha.CaptchaVerificationService;
import com.vocawik.service.debate.DebateService;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.service.resource.ResourcePopularityService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for Resource. */
@RestController
@Tag(name = "Resource", description = "Resource endpoints")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "DebateService is a Spring-managed bean reference and is not exposed externally.")
public class ResourceController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Map<String, String> ALLOWED_SORT_PROPERTIES =
            Map.of(
                    "updatedAt", "updatedAt",
                    "createdAt", "createdAt",
                    "name", "canonicalName",
                    "match", "match");

    private final ResourceService resourceService;
    private final DebateService debateService;
    private final ResourceHistoryService resourceHistoryService;
    private final ResourcePopularityService resourcePopularityService;
    private final CaptchaVerificationService captchaVerificationService;

    /**
     * Searches active resources filtered by status/query.
     *
     * @param status optional resource status filter
     * @param query optional canonical name query
     * @param pageable page and sort options
     * @return paged resource summaries
     */
    @GetMapping("/resources")
    @RateLimit(requests = 60, seconds = 60)
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
                                    "createdAt,desc",
                                    "name,asc",
                                    "name,desc",
                                    "match,asc"
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
                                sanitizeSort(pageable.getSort(), query))));
    }

    /** Suggests resources matching the current query. */
    @GetMapping("/resources/suggestions")
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Suggest resources",
            description = "Returns up to 10 resource suggestions matching the current query.")
    public ResponseEntity<ResourceSuggestionListResponse> suggestResources(
            @Parameter(description = "Suggestion query") @RequestParam(name = "query")
                    String query) {
        return ResponseEntity.ok(resourceService.suggest(query));
    }

    /** Returns resource ACL and history metadata. */
    @GetMapping("/resources/{resourceUuid}/info")
    @Operation(
            summary = "Get resource info",
            description = "Returns shared resource ACL and history metadata.")
    public ResponseEntity<ResourceInfoResponse> getResourceInfo(@PathVariable UUID resourceUuid) {
        return ResponseEntity.ok(resourceService.getResourceInfoByResourceUuid(resourceUuid));
    }

    /** Lists debate threads attached to a resource. */
    @GetMapping("/resources/{resourceUuid}/debates")
    @Operation(
            summary = "List debates",
            description = "Returns visible discussion threads attached to a resource.")
    public ResponseEntity<DebateListResponse> listDebates(@PathVariable UUID resourceUuid) {
        return ResponseEntity.ok(debateService.listByResourceUuid(resourceUuid));
    }

    /** Returns a single debate thread with its body and comments. */
    @GetMapping("/resources/{resourceUuid}/debates/{debateUuid}")
    @Operation(
            summary = "Get debate",
            description =
                    "Returns a debate thread with its first comment as the body and all remaining comments.")
    public ResponseEntity<DebateDetailResponse> getDebate(
            @PathVariable UUID resourceUuid, @PathVariable UUID debateUuid) {
        return ResponseEntity.ok(
                debateService.getByResourceUuidAndDebateUuid(resourceUuid, debateUuid));
    }

    /** Creates a debate thread. */
    @PostMapping("/resources/{resourceUuid}/debates")
    @AllowGuest
    @Operation(summary = "Create debate", description = "Creates a debate and its first comment.")
    public ResponseEntity<DebateListElementResponse> createDebate(
            @PathVariable UUID resourceUuid,
            @Valid @RequestBody DebateCreateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        return ResponseEntity.ok(debateService.create(resourceUuid, request));
    }

    /** Creates a debate comment or reply. */
    @PostMapping("/resources/{resourceUuid}/debates/{debateUuid}/comments")
    @AllowGuest
    @Operation(
            summary = "Create debate comment",
            description = "Creates a debate comment or reply.")
    public ResponseEntity<DebateCommentResponse> createDebateComment(
            @PathVariable UUID resourceUuid,
            @PathVariable UUID debateUuid,
            @Valid @RequestBody DebateCommentCreateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        return ResponseEntity.ok(debateService.createComment(resourceUuid, debateUuid, request));
    }

    /** Updates a debate comment. */
    @PatchMapping("/resources/{resourceUuid}/debates/{debateUuid}/comments/{commentUuid}")
    @AllowGuest
    @Operation(
            summary = "Update debate comment",
            description = "Updates an existing debate comment.")
    public ResponseEntity<DebateCommentResponse> updateDebateComment(
            @PathVariable UUID resourceUuid,
            @PathVariable UUID debateUuid,
            @PathVariable UUID commentUuid,
            @Valid @RequestBody DebateCommentUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        return ResponseEntity.ok(
                debateService.updateComment(resourceUuid, debateUuid, commentUuid, request));
    }

    /** Soft deletes a debate thread. */
    @DeleteMapping("/resources/{resourceUuid}/debates/{debateUuid}")
    @AllowGuest
    @Operation(
            summary = "Delete debate",
            description = "Soft deletes a debate thread for its author or an admin.")
    public ResponseEntity<Void> deleteDebate(
            @PathVariable UUID resourceUuid,
            @PathVariable UUID debateUuid,
            @RequestHeader(name = "X-Captcha-Token", required = false) String captchaToken,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(captchaToken, httpServletRequest);
        debateService.delete(resourceUuid, debateUuid);
        return ResponseEntity.noContent().build();
    }

    /** Updates a debate thread status. */
    @PatchMapping("/resources/{resourceUuid}/debates/{debateUuid}/status")
    @AllowGuest
    @Operation(
            summary = "Update debate status",
            description = "Updates a debate thread status for its author or an admin.")
    public ResponseEntity<DebateStatusResponse> updateDebateStatus(
            @PathVariable UUID resourceUuid,
            @PathVariable UUID debateUuid,
            @Valid @RequestBody DebateStatusUpdateRequest request,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(
                request.captchaToken(), httpServletRequest);
        return ResponseEntity.ok(debateService.updateStatus(resourceUuid, debateUuid, request));
    }

    /** Soft deletes a debate comment. */
    @DeleteMapping("/resources/{resourceUuid}/debates/{debateUuid}/comments/{commentUuid}")
    @AllowGuest
    @Operation(summary = "Delete debate comment", description = "Soft deletes a debate comment.")
    public ResponseEntity<Void> deleteDebateComment(
            @PathVariable UUID resourceUuid,
            @PathVariable UUID debateUuid,
            @PathVariable UUID commentUuid,
            @RequestHeader(name = "X-Captcha-Token", required = false) String captchaToken,
            HttpServletRequest httpServletRequest) {
        captchaVerificationService.verifyRequiredForNonUser(captchaToken, httpServletRequest);
        debateService.deleteComment(resourceUuid, debateUuid, commentUuid);
        return ResponseEntity.noContent().build();
    }

    /** Lists the most recent resource changes across all resource types. */
    @GetMapping("/histories/recent")
    @RateLimit(requests = 30, seconds = 60)
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

    /** Lists popular resources over the last 60 minutes. */
    @GetMapping("/resources/popular")
    @RateLimit(requests = 30, seconds = 60)
    @Operation(
            summary = "List popular resources",
            description = "Returns the most viewed public resources during the last 60 minutes.")
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
