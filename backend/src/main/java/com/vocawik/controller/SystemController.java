package com.vocawik.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint for external status checks. */
@RestController
@Tag(name = "System", description = "System status endpoints")
public class SystemController {

    /**
     * Returns a lightweight success response for status checks.
     *
     * @return response body without payload data
     */
    @GetMapping("/status")
    @Operation(
            summary = "Service status check",
            description = "Returns a lightweight success response for status checks.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Service is reachable",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(type = "object"),
                                examples = @ExampleObject(value = "{}")))
    })
    public Map<String, Object> status() {
        return Map.of();
    }
}
