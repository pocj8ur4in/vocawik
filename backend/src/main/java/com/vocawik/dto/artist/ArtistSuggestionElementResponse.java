package com.vocawik.dto.artist;

import java.util.UUID;

/** Suggestion item for artist autocomplete responses. */
public record ArtistSuggestionElementResponse(
        UUID resourceUuid, String name, boolean hasMultipleResources) {}
