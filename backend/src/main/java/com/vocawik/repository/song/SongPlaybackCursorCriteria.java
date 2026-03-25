package com.vocawik.repository.song;

import java.time.LocalDateTime;

/** Typed cursor payload for playback keyset pagination. */
public record SongPlaybackCursorCriteria(
        String sortProperty,
        boolean ascending,
        String stringValue,
        Long longValue,
        LocalDateTime dateTimeValue,
        Integer intValue,
        Integer secondaryIntValue,
        Long songId) {}
