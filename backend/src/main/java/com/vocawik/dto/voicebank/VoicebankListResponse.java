package com.vocawik.dto.voicebank;

import java.util.List;

/** Response payload for voicebank list queries. */
public record VoicebankListResponse(
        List<VoicebankElementResponse> items, int page, int size, boolean hasNext) {

    /** Creates an immutable list response. */
    public VoicebankListResponse {
        items = List.copyOf(items);
    }
}
