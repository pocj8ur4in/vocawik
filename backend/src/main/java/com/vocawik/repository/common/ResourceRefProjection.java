package com.vocawik.repository.common;

import java.util.UUID;

/** projection for entity id and resource UUID pair. */
public interface ResourceRefProjection {

    /** Internal database id. */
    Long getId();

    /** External resource UUID. */
    UUID getResourceUuid();
}
