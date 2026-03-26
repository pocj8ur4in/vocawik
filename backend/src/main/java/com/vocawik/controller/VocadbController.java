package com.vocawik.controller;

import com.vocawik.aop.RateLimit;
import com.vocawik.dto.vocadb.VocadbPrefillRequest;
import com.vocawik.dto.vocadb.VocadbPrefillResponse;
import com.vocawik.security.guest.AllowGuest;
import com.vocawik.service.vocadb.VocadbPrefillService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for VocaDB prefill resolution. */
@RestController
@Tag(name = "VocaDB", description = "VocaDB utility endpoints")
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "VocadbPrefillService is a Spring-managed bean reference and is not exposed externally.")
public class VocadbController {

    private final VocadbPrefillService vocadbPrefillService;

    @PostMapping("/vocadb/prefills")
    @AllowGuest
    @RateLimit(requests = 60, seconds = 60)
    @Operation(
            summary = "Resolve VocaDB prefill",
            description =
                    "Resolves create-form prefill payload from dump_vocadb_song or dump_vocadb_artist using a VocaDB link.")
    public ResponseEntity<VocadbPrefillResponse> resolvePrefill(
            @Valid @RequestBody VocadbPrefillRequest request) {
        return ResponseEntity.ok(vocadbPrefillService.resolve(request.url()));
    }
}
