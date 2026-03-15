package com.vocawik.dto.song;

import jakarta.validation.constraints.NotBlank;

/** Request payload for resolving song PV metadata from URL. */
public record SongPvResolveRequest(@NotBlank String url) {}
