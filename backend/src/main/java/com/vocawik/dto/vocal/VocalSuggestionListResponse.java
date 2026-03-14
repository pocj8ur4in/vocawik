package com.vocawik.dto.vocal;

import java.util.List;

/** Response payload for vocal suggestion queries. */
public record VocalSuggestionListResponse(List<VocalSuggestionElementResponse> items) {

    /** Creates an immutable list response. */
    public VocalSuggestionListResponse {
        items = List.copyOf(items);
    }
}
