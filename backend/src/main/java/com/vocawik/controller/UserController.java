package com.vocawik.controller;

import com.vocawik.dto.user.UserMeResponse;
import com.vocawik.security.CurrentUser;
import com.vocawik.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the current authenticated user. */
@RestController
@Tag(name = "User", description = "User endpoints")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** Returns the current authenticated user's profile. */
    @GetMapping("/users/me")
    @Operation(
            summary = "Get current user",
            description = "Returns the authenticated user's profile.")
    public ResponseEntity<UserMeResponse> getCurrentUser(
            @Parameter(hidden = true) @CurrentUser UUID userUuid) {
        return ResponseEntity.ok(userService.getCurrentUser(userUuid));
    }
}
