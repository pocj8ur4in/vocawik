package com.vocawik.infrastructure.pv.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvMetaApiClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;

/** Bandcamp page parser client for fetching track metadata from URL. */
@Component
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "ObjectMapper is a Spring-managed infrastructure bean and is not exposed externally.")
public class BandcampPvMetaApiClient implements PvMetaApiClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final DateTimeFormatter BANDCAMP_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private static final Pattern TRALBUM_PATTERN =
            Pattern.compile("data-tralbum\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern EMBED_PATTERN = Pattern.compile("data-embed\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern OG_IMAGE_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]*property\\s*=\\s*['\"]og:image['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
    private static final Pattern OG_URL_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]*property\\s*=\\s*['\"]og:url['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");

    private final PvHttpClientSupport pvHttpClientSupport;
    private final ObjectMapper objectMapper;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.BANDCAMP;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.BANDCAMP) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        String requestUrl = normalizeSourceUrl(detectedPv);
        RestClient restClient = pvHttpClientSupport.createRestClient(null, null);

        String responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(requestUrl)
                                            .header("User-Agent", USER_AGENT)
                                            .retrieve()
                                            .body(String.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(
                        "bandcamp track not found: " + detectedPv.videoKey(), ex);
            }
            throw new IllegalStateException("failed to fetch bandcamp pv metadata", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch bandcamp pv metadata", ex);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("bandcamp metadata response is empty");
        }

        JsonNode tralbum = parseDataAttributeAsJson(responseBody, TRALBUM_PATTERN);
        JsonNode embed = parseDataAttributeAsJson(responseBody, EMBED_PATTERN);
        JsonNode track = extractTrackNode(tralbum);

        String trackId =
                firstNonBlank(
                        text(track, "track_id"), text(track, "id"), text(tralbum, "track_id"));
        String title = firstNonBlank(text(track, "title"), text(embed, "title"));
        String artist =
                firstNonBlank(
                        text(track, "artist"), text(tralbum, "artist"), text(embed, "artist"));
        String finalTitle = buildTitle(title, artist);

        String thumbnailUrl = extractMetaContent(responseBody, OG_IMAGE_PATTERN);
        String externalUrl =
                firstNonBlank(extractMetaContent(responseBody, OG_URL_PATTERN), requestUrl);
        String uploaderKey = extractUploaderKey(requestUrl, artist);
        Integer durationSeconds =
                parseDurationSeconds(track == null ? null : track.get("duration"));
        String publishedAt = parsePublishedAt(extractPublishDate(tralbum));
        PvMetaExtra extra = externalUrl == null ? null : new PvMetaExtra(null, null, externalUrl);

        if (finalTitle == null) {
            throw new IllegalArgumentException(
                    "bandcamp track not found: " + detectedPv.videoKey());
        }

        return new PvMetaResult(
                firstNonBlank(trackId, detectedPv.videoKey()),
                finalTitle,
                thumbnailUrl,
                uploaderKey,
                durationSeconds,
                publishedAt,
                extra);
    }

    private String normalizeSourceUrl(DetectedPv detectedPv) {
        String normalizedUrl = nullIfBlank(detectedPv.normalizedUrl());
        if (normalizedUrl == null) {
            throw new IllegalArgumentException("bandcamp url is required");
        }
        return normalizedUrl;
    }

    private JsonNode parseDataAttributeAsJson(String html, Pattern pattern) {
        String encoded = extractFirstGroup(html, pattern);
        if (encoded == null) {
            return null;
        }
        try {
            String decoded = HtmlUtils.htmlUnescape(encoded);
            return objectMapper.readTree(decoded);
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode extractTrackNode(JsonNode tralbum) {
        if (tralbum == null) {
            return null;
        }
        JsonNode trackInfo = tralbum.get("trackinfo");
        if (trackInfo == null || !trackInfo.isArray() || trackInfo.isEmpty()) {
            return null;
        }
        return trackInfo.get(0);
    }

    private String extractPublishDate(JsonNode tralbum) {
        if (tralbum == null) {
            return null;
        }
        JsonNode current = tralbum.get("current");
        return firstNonBlank(text(current, "publish_date"), text(current, "release_date"));
    }

    private String extractMetaContent(String html, Pattern pattern) {
        String value = extractFirstGroup(html, pattern);
        return value == null ? null : nullIfBlank(HtmlUtils.htmlUnescape(value));
    }

    private String extractFirstGroup(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        return matcher.group(1);
    }

    private String buildTitle(String title, String artist) {
        String normalizedTitle = nullIfBlank(title);
        String normalizedArtist = nullIfBlank(artist);
        if (normalizedTitle == null) {
            return null;
        }
        if (normalizedArtist == null) {
            return normalizedTitle;
        }
        if (normalizedTitle.startsWith(normalizedArtist + " - ")) {
            return normalizedTitle;
        }
        return normalizedArtist + " - " + normalizedTitle;
    }

    private String extractUploaderKey(String sourceUrl, String fallbackArtist) {
        String normalized = nullIfBlank(sourceUrl);
        if (normalized == null) {
            return nullIfBlank(fallbackArtist);
        }
        try {
            URI uri = URI.create(normalized);
            String host = nullIfBlank(uri.getHost());
            if (host == null) {
                return nullIfBlank(fallbackArtist);
            }
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            String suffix = ".bandcamp.com";
            if (host.endsWith(suffix) && host.length() > suffix.length()) {
                return host.substring(0, host.length() - suffix.length());
            }
            return nullIfBlank(fallbackArtist);
        } catch (RuntimeException ex) {
            return nullIfBlank(fallbackArtist);
        }
    }

    private Integer parseDurationSeconds(JsonNode durationNode) {
        if (durationNode == null || durationNode.isNull()) {
            return null;
        }
        try {
            double raw =
                    durationNode.isNumber()
                            ? durationNode.asDouble()
                            : Double.parseDouble(durationNode.asText());
            if (raw < 0) {
                return null;
            }
            return (int) Math.round(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String parsePublishedAt(String rawPublishedAt) {
        String normalized = nullIfBlank(rawPublishedAt);
        if (normalized == null) {
            return null;
        }

        try {
            return Instant.parse(normalized).toString();
        } catch (RuntimeException ignored) {
            // continue to next parser
        }

        try {
            return OffsetDateTime.parse(normalized).toInstant().toString();
        } catch (RuntimeException ignored) {
            // continue to next parser
        }

        try {
            return ZonedDateTime.parse(normalized, BANDCAMP_DATE_FORMATTER).toInstant().toString();
        } catch (RuntimeException ignored) {
            // continue to next parser
        }

        try {
            return LocalDateTime.parse(normalized, BANDCAMP_DATE_FORMATTER)
                    .toInstant(ZoneOffset.UTC)
                    .toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || field == null || field.isBlank()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return nullIfBlank(HtmlUtils.htmlUnescape(value.asText()));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
