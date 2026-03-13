package com.vocawik.dto.resource;

import java.util.List;

/** Response for the recent popular resources feed. */
public record PopularResourceListResponse(List<PopularResourceElementResponse> items, int size) {

    public PopularResourceListResponse {
        items = List.copyOf(items);
    }
}
