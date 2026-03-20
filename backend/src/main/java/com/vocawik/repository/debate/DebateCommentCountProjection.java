package com.vocawik.repository.debate;

/** Projection for active comment counts grouped by debate. */
public interface DebateCommentCountProjection {

    Long getDebateId();

    long getCommentCount();
}
