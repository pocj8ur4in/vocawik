package com.vocawik.module.security.guest;

import java.util.UUID;

/**
 * Authenticated guest principal.
 *
 * @param guestUuid guest UUID
 */
public record GuestPrincipal(UUID guestUuid) {}
