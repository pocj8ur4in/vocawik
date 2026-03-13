package com.vocawik.repository.history;

import com.vocawik.domain.history.HistoryActionType;
import java.time.LocalDateTime;

/** Projection for recent change rows joined with resource and actor user metadata. */
public interface RecentChangeProjection {

    LocalDateTime getCreatedAt();

    String getCanonicalName();

    HistoryActionType getActionType();

    String getActorUserNickname();
}
