package com.vocawik.controller;

import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.vocal.VoicebankType;
import com.vocawik.dto.voicebank.VoicebankListResponse;
import com.vocawik.service.vocal.VoicebankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for voicebank. */
@RestController
@Tag(name = "Voicebank", description = "Voicebank endpoints")
@RequiredArgsConstructor
public class VoicebankController {

    private static final String DEFAULT_SORT_PROPERTY = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;
    private static final Map<String, String> ALLOWED_SORT_PROPERTIES =
            Map.of(
                    "updatedAt", "resource.updatedAt",
                    "createdAt", "resource.createdAt");

    private final VoicebankService voicebankService;

    /**
     * Searches voicebanks with optional filters.
     *
     * @param status optional resource status filter
     * @param query optional name keyword
     * @param songUuids optional song resource UUID filter
     * @param vocalUuids optional vocal character resource UUID filter
     * @param voicebankTypes optional voicebank type filter
     * @param pageable page and sort options
     * @return paged voicebank summaries
     */
    @GetMapping("/voicebanks")
    @Operation(
            summary = "Search voicebanks",
            description = "Returns active voicebanks with optional filters.")
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
    public ResponseEntity<VoicebankListResponse> searchVoicebanks(
            @Parameter(description = "Resource status filter")
                    @RequestParam(name = "status", required = false)
                    ResourceStatus status,
            @Parameter(description = "Name keyword") @RequestParam(name = "query", required = false)
                    String query,
            @Parameter(description = "Song resource UUID filter")
                    @RequestParam(name = "songUuids", required = false)
                    List<UUID> songUuids,
            @Parameter(description = "Vocal character resource UUID filter")
                    @RequestParam(name = "vocalUuids", required = false)
                    List<UUID> vocalUuids,
            @Parameter(description = "Voicebank type filter")
                    @RequestParam(name = "voicebankTypes", required = false)
                    List<VoicebankType> voicebankTypes,
            @Parameter(hidden = true)
                    @PageableDefault(
                            size = 20,
                            sort = DEFAULT_SORT_PROPERTY,
                            direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(
                voicebankService.search(
                        status,
                        query,
                        songUuids,
                        vocalUuids,
                        voicebankTypes,
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
