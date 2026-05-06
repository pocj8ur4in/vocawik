/**
 * Reusable authentication and fail closed HTTP security support discovered through Spring Boot
 * automatic configuration metadata.
 *
 * <p>The module final catch all chain is enabled by default. A consumer may assume complete HTTP
 * security ownership only by setting {@code security.http.default-chain-enabled=false} and
 * providing its own {@code SecurityFilterChain}.
 */
package com.vocawik.module.security;
