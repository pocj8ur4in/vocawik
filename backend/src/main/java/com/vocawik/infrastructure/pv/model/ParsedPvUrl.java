package com.vocawik.infrastructure.pv.model;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parsed URL fields shared across PV URL detectors. */
public record ParsedPvUrl(
        URI uri,
        String host,
        String path,
        List<String> pathSegments,
        Map<String, String> queryParams,
        String normalizedUrl) {

    public ParsedPvUrl {
        if (uri == null) {
            throw new IllegalArgumentException("uri is required");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }
        path = path == null ? "" : path;
        pathSegments = pathSegments == null ? List.of() : List.copyOf(pathSegments);
        queryParams = queryParams == null ? Map.of() : Map.copyOf(queryParams);
        if (normalizedUrl == null || normalizedUrl.isBlank()) {
            throw new IllegalArgumentException("normalizedUrl is required");
        }
    }

    /** Parses and normalizes raw URL for detector usage. */
    public static ParsedPvUrl parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        URI parsedUri = toUri(rawUrl.trim());
        String normalizedHost = normalizeHost(parsedUri.getHost());
        String normalizedPath = parsedUri.getPath() == null ? "" : parsedUri.getPath();
        return new ParsedPvUrl(
                parsedUri,
                normalizedHost,
                normalizedPath,
                parsePathSegments(normalizedPath),
                parseQueryParams(parsedUri.getRawQuery()),
                parsedUri.toString());
    }

    private static URI toUri(String value) {
        try {
            URI parsed = URI.create(value);
            if (parsed.getHost() != null) {
                return parsed;
            }
            if (!value.contains("://")) {
                parsed = URI.create("https://" + value);
                if (parsed.getHost() != null) {
                    return parsed;
                }
            }
            throw new IllegalArgumentException("url host is required");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("url is invalid");
        }
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("url host is required");
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("www.")) {
            return normalized.substring(4);
        }
        return normalized;
    }

    private static List<String> parsePathSegments(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            segments.add(segment);
        }
        return segments;
    }

    private static Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }

        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            String key;
            String value;
            if (separatorIndex < 0) {
                key = decode(pair);
                value = "";
            } else {
                key = decode(pair.substring(0, separatorIndex));
                value = decode(pair.substring(separatorIndex + 1));
            }
            if (key.isBlank()) {
                continue;
            }
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return value;
        }
    }
}
