package com.vocawik.controller;

import com.vocawik.dto.stats.StatsResponse;
import com.vocawik.service.stats.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for site-wide aggregate stats. */
@RestController
@Tag(name = "Stats", description = "Stats endpoints")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** Returns the latest daily stats snapshot. */
    @GetMapping("/stats")
    @Operation(summary = "Get stats", description = "Returns the latest daily stats snapshot.")
    public ResponseEntity<StatsResponse> getLatestStats() {
        return ResponseEntity.ok(statsService.getLatest());
    }
}
