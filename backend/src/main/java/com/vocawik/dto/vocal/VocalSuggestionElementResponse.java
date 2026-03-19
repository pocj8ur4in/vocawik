package com.vocawik.dto.vocal;

import java.util.UUID;

/** Suggestion item for vocal autocomplete responses. */
public record VocalSuggestionElementResponse(
        UUID resourceUuid, String name, String localizedName, boolean hasMultipleResources) {}
