package com.vocawik.dto.playlist;

import java.util.UUID;

/** Suggestion item for playlist autocomplete results. */
public record PlaylistSuggestionElementResponse(UUID resourceUuid, String name) {}
