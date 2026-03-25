package com.vocawik.dto.resource;

import com.vocawik.dto.history.ResourceHistoryElementResponse;
import java.util.List;
import java.util.UUID;

/** Shared resource info payload containing ACL and history metadata. */
public record ResourceInfoResponse(
        UUID resourceUuid,
        String status,
        boolean isDeleted,
        List<ResourceAclDetailResponse> acls,
        List<ResourceHistoryElementResponse> histories) {

    /** Creates an immutable info response. */
    public ResourceInfoResponse {
        acls = List.copyOf(acls);
        histories = List.copyOf(histories);
    }
}
