package com.vocawik.dto.resource;

import java.util.UUID;

/** Suggestion item for resource autocomplete responses. */
public record ResourceSuggestionElementResponse(UUID uuid, String name) {}
