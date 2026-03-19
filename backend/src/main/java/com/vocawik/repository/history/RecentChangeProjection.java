package com.vocawik.repository.history;

import com.vocawik.domain.history.HistoryActionType;
import com.vocawik.domain.resource.ResourceType;
import java.time.LocalDateTime;
import java.util.UUID;

/** Projection for recent change rows joined with resource and actor user metadata. */
public interface RecentChangeProjection {

    LocalDateTime getCreatedAt();

    Long getResourceId();

    UUID getResourceUuid();

    String getCanonicalName();

    ResourceType getResourceType();

    HistoryActionType getActionType();

    String getActorUserNickname();
}
