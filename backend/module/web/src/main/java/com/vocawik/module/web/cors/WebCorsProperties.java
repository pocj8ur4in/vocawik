package com.vocawik.module.web.cors;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Properties for CORS policy.
 *
 * @param allowedOrigins origins allowed to call backend APIs
 * @param allowedMethods HTTP methods allowed for cross-origin requests
 * @param allowedHeaders request headers allowed for cross-origin requests
 * @param exposedHeaders response headers exposed to browser clients
 * @param allowCredentials whether credentials are allowed for cross-origin requests
 * @param maxAge preflight cache duration in seconds
 * @param pathPattern URL path pattern this CORS policy applies to
 */
@ConfigurationProperties(prefix = "web.cors")
public record WebCorsProperties(
        @DefaultValue List<String> allowedOrigins,
        @DefaultValue({"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"})
                List<String> allowedMethods,
        @DefaultValue("*") List<String> allowedHeaders,
        @DefaultValue({"X-Request-Id", "X-Client-IP"}) List<String> exposedHeaders,
        @DefaultValue("true") boolean allowCredentials,
        @DefaultValue("3600") Long maxAge,
        @DefaultValue("/**") String pathPattern) {

    /**
     * Creates an immutable CORS policy and rejects credential settings that browsers cannot apply
     * safely with a wildcard origin.
     *
     * @param allowedOrigins origins allowed to call backend APIs
     * @param allowedMethods HTTP methods allowed for CORS requests
     * @param allowedHeaders request headers allowed for CORS requests
     * @param exposedHeaders response headers exposed to browser clients
     * @param allowCredentials whether credentials are allowed for CORS requests
     * @param maxAge preflight cache duration in seconds
     * @param pathPattern URL path pattern governed by this policy
     * @throws IllegalArgumentException if credentials are enabled with a wildcard allowed origin
     * @throws NullPointerException if a policy list or one of its elements is null
     */
    public WebCorsProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
        allowedMethods = List.copyOf(allowedMethods);
        allowedHeaders = List.copyOf(allowedHeaders);
        exposedHeaders = List.copyOf(exposedHeaders);

        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "web.cors.allowed-origins cannot contain '*' when "
                            + "web.cors.allow-credentials is true");
        }
    }
}
