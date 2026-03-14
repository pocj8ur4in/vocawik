package com.vocawik.infrastructure.pv.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvApiProperties;
import com.vocawik.service.pv.client.PvMetaApiClient;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** SoundCloud metadata client using an external proxy API endpoint. */
@Component
@RequiredArgsConstructor
public class SoundCloudPvMetaApiClient implements PvMetaApiClient {

    private static final DateTimeFormatter SOUNDCLOUD_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z", Locale.ENGLISH);

    private static final String DEFAULT_PROXY_BASE_URL =
            "https://dream-traveler.fly.dev/soundcloud/api";
    private static final String DEFAULT_OEMBED_BASE_URL = "https://soundcloud.com/oembed";

    private final PvApiProperties pvApiProperties;
    private final PvHttpClientSupport pvHttpClientSupport;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.SOUNDCLOUD;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.SOUNDCLOUD) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        PvApiProperties.SoundCloud soundCloudProperties = pvApiProperties.getSoundCloud();
        String proxyBaseUrl =
                firstNonBlank(
                        nullIfBlank(soundCloudProperties.getProxyBaseUrl()),
                        DEFAULT_PROXY_BASE_URL);
        String oembedBaseUrl =
                firstNonBlank(
                        nullIfBlank(soundCloudProperties.getOembedBaseUrl()),
                        DEFAULT_OEMBED_BASE_URL);
        String sourceUrl = buildSourceUrl(detectedPv);

        RestClient restClient =
                pvHttpClientSupport.createRestClient(
                        soundCloudProperties.getConnectTimeoutMs(),
                        soundCloudProperties.getReadTimeoutMs());
        RuntimeException proxyFailure = null;
        try {
            PvMetaResult proxyResult =
                    fetchFromProxy(restClient, proxyBaseUrl, sourceUrl, detectedPv);
            if (proxyResult != null) {
                return proxyResult;
            }
        } catch (RuntimeException ex) {
            proxyFailure = ex;
        }

        try {
            return fetchFromOEmbed(restClient, oembedBaseUrl, sourceUrl, detectedPv);
        } catch (RuntimeException oembedFailure) {
            if (proxyFailure != null) {
                proxyFailure.addSuppressed(oembedFailure);
                throw proxyFailure;
            }
            throw oembedFailure;
        }
    }

    private PvMetaResult fetchFromProxy(
            RestClient restClient, String proxyBaseUrl, String sourceUrl, DetectedPv detectedPv) {
        String requestUrl =
                UriComponentsBuilder.fromUriString(proxyBaseUrl)
                        .queryParam("url", sourceUrl)
                        .build(true)
                        .toUriString();

        SoundCloudProxyResponse responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(requestUrl)
                                            .retrieve()
                                            .body(SoundCloudProxyResponse.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                return null;
            }
            throw new IllegalStateException("failed to fetch soundcloud pv metadata", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch soundcloud pv metadata", ex);
        }
        if (responseBody == null) {
            return null;
        }
        String title = nullIfBlank(responseBody.title());
        if (title == null) {
            return null;
        }

        String thumbnailUrl =
                firstNonBlank(
                        nullIfBlank(responseBody.artworkUrl()),
                        responseBody.user() == null
                                ? null
                                : nullIfBlank(responseBody.user().avatarUrl()));
        String uploaderKey =
                responseBody.user() == null ? null : nullIfBlank(responseBody.user().permalink());
        Integer durationSeconds = toDurationSeconds(responseBody.duration());
        String publishedAt = toPublishedAt(responseBody.createdAt());

        return new PvMetaResult(
                detectedPv.videoKey(),
                title,
                thumbnailUrl,
                uploaderKey,
                durationSeconds,
                publishedAt);
    }

    private PvMetaResult fetchFromOEmbed(
            RestClient restClient, String oembedBaseUrl, String sourceUrl, DetectedPv detectedPv) {
        String requestUrl =
                UriComponentsBuilder.fromUriString(oembedBaseUrl)
                        .queryParam("format", "json")
                        .queryParam("url", sourceUrl)
                        .build(true)
                        .toUriString();
        SoundCloudOEmbedResponse responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(requestUrl)
                                            .retrieve()
                                            .body(SoundCloudOEmbedResponse.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(
                        "soundcloud track not found: " + detectedPv.videoKey(), ex);
            }
            throw new IllegalStateException("failed to fetch soundcloud pv metadata", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch soundcloud pv metadata", ex);
        }
        if (responseBody == null || nullIfBlank(responseBody.title()) == null) {
            throw new IllegalArgumentException(
                    "soundcloud track not found: " + detectedPv.videoKey());
        }

        String authorName = nullIfBlank(responseBody.authorName());
        String title = normalizeOEmbedTitle(responseBody.title(), authorName);
        String uploaderKey =
                firstNonBlank(extractUploaderKey(responseBody.authorUrl()), authorName);

        return new PvMetaResult(
                detectedPv.videoKey(),
                title,
                nullIfBlank(responseBody.thumbnailUrl()),
                uploaderKey,
                null,
                null);
    }

    private String buildSourceUrl(DetectedPv detectedPv) {
        String normalizedUrl = nullIfBlank(detectedPv.normalizedUrl());
        if (normalizedUrl != null) {
            return normalizedUrl;
        }
        return "https://soundcloud.com/" + detectedPv.videoKey();
    }

    private String normalizeOEmbedTitle(String rawTitle, String authorName) {
        String normalizedTitle = nullIfBlank(rawTitle);
        if (normalizedTitle == null) {
            return null;
        }
        String normalizedAuthor = nullIfBlank(authorName);
        if (normalizedAuthor == null) {
            return normalizedTitle;
        }

        String suffix = " by " + normalizedAuthor;
        if (normalizedTitle.endsWith(suffix)) {
            String withoutSuffix =
                    normalizedTitle.substring(0, normalizedTitle.length() - suffix.length());
            String trimmed = nullIfBlank(withoutSuffix);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return normalizedTitle;
    }

    private String extractUploaderKey(String authorUrl) {
        String normalized = nullIfBlank(authorUrl);
        if (normalized == null) {
            return null;
        }
        try {
            URI uri = URI.create(normalized);
            String path = nullIfBlank(uri.getPath());
            if (path == null) {
                return null;
            }
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                String segment = nullIfBlank(segments[i]);
                if (segment != null) {
                    return segment;
                }
            }
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer toDurationSeconds(Integer durationMillis) {
        if (durationMillis == null || durationMillis < 0) {
            return null;
        }
        return durationMillis / 1000;
    }

    private String toPublishedAt(String rawCreatedAt) {
        String normalized = nullIfBlank(rawCreatedAt);
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
            return OffsetDateTime.parse(normalized, SOUNDCLOUD_DATE_FORMATTER)
                    .toInstant()
                    .toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SoundCloudProxyResponse(
            String id,
            String title,
            @JsonProperty("artwork_url") String artworkUrl,
            @JsonProperty("duration") Integer duration,
            @JsonProperty("created_at") String createdAt,
            SoundCloudUser user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SoundCloudUser(
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("permalink") String permalink,
            @JsonProperty("username") String username) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SoundCloudOEmbedResponse(
            String title,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("author_url") String authorUrl,
            @JsonProperty("author_name") String authorName) {}
}
