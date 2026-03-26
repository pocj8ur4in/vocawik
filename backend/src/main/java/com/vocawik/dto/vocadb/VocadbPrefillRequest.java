package com.vocawik.dto.vocadb;

import jakarta.validation.constraints.NotBlank;

/** Request payload for VocaDB link prefill resolution. */
public record VocadbPrefillRequest(@NotBlank String url) {}
