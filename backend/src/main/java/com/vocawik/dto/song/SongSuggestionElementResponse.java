package com.vocawik.dto.song;

import java.util.UUID;

/** Suggestion item for song autocomplete responses. */
public record SongSuggestionElementResponse(
        UUID resourceUuid, String name, String localizedName, boolean hasMultipleResources) {}
