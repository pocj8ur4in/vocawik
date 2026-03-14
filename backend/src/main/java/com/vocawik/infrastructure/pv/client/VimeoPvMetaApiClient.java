package com.vocawik.infrastructure.pv.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvApiProperties;
import com.vocawik.service.pv.client.PvMetaApiClient;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/** Vimeo metadata client using Vimeo API with oEmbed fallback. */
@Component
@RequiredArgsConstructor
public class VimeoPvMetaApiClient implements PvMetaApiClient {

    private static final String DEFAULT_API_BASE_URL = "https://api.vimeo.com";
    private static final String DEFAULT_OEMBED_BASE_URL = "https://vimeo.com/api/oembed.json";

    private static final DateTimeFormatter VIMEO_OEMBED_UPLOAD_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PvApiProperties pvApiProperties;
    private final PvHttpClientSupport pvHttpClientSupport;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.VIMEO;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.VIMEO) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        PvApiProperties.Vimeo vimeoProperties = pvApiProperties.getVimeo();
        String apiBaseUrl =
                firstNonBlank(nullIfBlank(vimeoProperties.getBaseUrl()), DEFAULT_API_BASE_URL);
        String oembedBaseUrl =
                firstNonBlank(
                        nullIfBlank(vimeoProperties.getOembedBaseUrl()), DEFAULT_OEMBED_BASE_URL);
        String sourceUrl = buildSourceUrl(detectedPv);

        RestClient restClient =
                pvHttpClientSupport.createRestClient(
                        vimeoProperties.getConnectTimeoutMs(), vimeoProperties.getReadTimeoutMs());

        RuntimeException apiFailure = null;
        String accessToken = nullIfBlank(vimeoProperties.getAccessToken());
        if (accessToken != null) {
            try {
                PvMetaResult apiResult =
                        fetchFromApi(restClient, apiBaseUrl, accessToken, detectedPv);
                if (apiResult != null) {
                    return apiResult;
                }
            } catch (RuntimeException ex) {
                apiFailure = ex;
            }
        }

        try {
            return fetchFromOEmbed(restClient, oembedBaseUrl, sourceUrl, detectedPv);
        } catch (RuntimeException oembedFailure) {
            if (apiFailure != null) {
                apiFailure.addSuppressed(oembedFailure);
                throw apiFailure;
            }
            throw oembedFailure;
        }
    }

    private PvMetaResult fetchFromApi(
            RestClient restClient, String apiBaseUrl, String accessToken, DetectedPv detectedPv) {
        String requestUrl =
                UriComponentsBuilder.fromUriString(apiBaseUrl)
                        .path("/videos/{videoId}")
                        .buildAndExpand(detectedPv.videoKey())
                        .toUriString();

        VimeoApiResponse responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(requestUrl)
                                            .header("Authorization", "Bearer " + accessToken)
                                            .retrieve()
                                            .body(VimeoApiResponse.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return null;
            }
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new IllegalStateException("failed to fetch vimeo pv metadata", ex);
            }
            if (ex.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(
                        "vimeo video not found: " + detectedPv.videoKey(), ex);
            }
            throw new IllegalStateException("failed to fetch vimeo pv metadata", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch vimeo pv metadata", ex);
        }

        if (responseBody == null || nullIfBlank(responseBody.name()) == null) {
            return null;
        }

        String videoKey =
                firstNonBlank(extractVideoKeyFromUri(responseBody.uri()), detectedPv.videoKey());
        return new PvMetaResult(
                videoKey,
                nullIfBlank(responseBody.name()),
                extractThumbnailUrl(responseBody.pictures()),
                extractUploaderKey(responseBody.user()),
                toDurationSeconds(responseBody.duration()),
                toPublishedAt(responseBody.createdTime()));
    }

    private PvMetaResult fetchFromOEmbed(
            RestClient restClient, String oembedBaseUrl, String sourceUrl, DetectedPv detectedPv) {
        String requestUrl =
                UriComponentsBuilder.fromUriString(oembedBaseUrl)
                        .queryParam("format", "json")
                        .queryParam("url", sourceUrl)
                        .build(true)
                        .toUriString();

        VimeoOEmbedResponse responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(requestUrl)
                                            .retrieve()
                                            .body(VimeoOEmbedResponse.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError()) {
                throw new IllegalArgumentException(
                        "vimeo video not found: " + detectedPv.videoKey(), ex);
            }
            throw new IllegalStateException("failed to fetch vimeo pv metadata", ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch vimeo pv metadata", ex);
        }

        if (responseBody == null || nullIfBlank(responseBody.title()) == null) {
            throw new IllegalArgumentException("vimeo video not found: " + detectedPv.videoKey());
        }

        String videoKey =
                firstNonBlank(
                        responseBody.videoId() == null
                                ? null
                                : String.valueOf(responseBody.videoId()),
                        detectedPv.videoKey());
        String uploaderKey =
                firstNonBlank(
                        extractUploaderKeyFromAuthorUrl(responseBody.authorUrl()),
                        nullIfBlank(responseBody.authorName()));

        return new PvMetaResult(
                videoKey,
                nullIfBlank(responseBody.title()),
                nullIfBlank(responseBody.thumbnailUrl()),
                uploaderKey,
                toDurationSeconds(responseBody.duration()),
                toPublishedAt(responseBody.uploadDate()));
    }

    private String buildSourceUrl(DetectedPv detectedPv) {
        String normalizedUrl = nullIfBlank(detectedPv.normalizedUrl());
        if (normalizedUrl != null) {
            return normalizedUrl;
        }
        return "https://vimeo.com/" + detectedPv.videoKey();
    }

    private String extractVideoKeyFromUri(String uri) {
        String normalized = nullIfBlank(uri);
        if (normalized == null) {
            return null;
        }
        int slash = normalized.lastIndexOf('/');
        if (slash < 0 || slash >= normalized.length() - 1) {
            return null;
        }
        String candidate = normalized.substring(slash + 1).trim();
        return candidate.isEmpty() ? null : candidate;
    }

    private String extractThumbnailUrl(VimeoPictures pictures) {
        if (pictures == null || pictures.sizes() == null || pictures.sizes().isEmpty()) {
            return null;
        }

        String bestUrl = null;
        int bestWidth = -1;
        for (VimeoPicture picture : pictures.sizes()) {
            if (picture == null) {
                continue;
            }
            String link = nullIfBlank(picture.link());
            if (link == null) {
                continue;
            }
            int width = picture.width() == null ? 0 : picture.width();
            if (width >= bestWidth) {
                bestWidth = width;
                bestUrl = link;
            }
        }
        return bestUrl;
    }

    private String extractUploaderKey(VimeoUser user) {
        if (user == null) {
            return null;
        }
        return firstNonBlank(extractUploaderKeyFromAuthorUrl(user.uri()), nullIfBlank(user.name()));
    }

    private String extractUploaderKeyFromAuthorUrl(String authorUrl) {
        String normalized = nullIfBlank(authorUrl);
        if (normalized == null) {
            return null;
        }

        try {
            URI parsed =
                    URI.create(
                            normalized.startsWith("/")
                                    ? "https://vimeo.com" + normalized
                                    : normalized);
            String path = nullIfBlank(parsed.getPath());
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

    private Integer toDurationSeconds(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds < 0) {
            return null;
        }
        return durationSeconds;
    }

    private String toPublishedAt(String rawPublishedAt) {
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
            return LocalDateTime.parse(normalized, VIMEO_OEMBED_UPLOAD_DATE_FORMATTER)
                    .toInstant(ZoneOffset.UTC)
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
    private record VimeoApiResponse(
            String name,
            Integer duration,
            @JsonProperty("created_time") String createdTime,
            String uri,
            VimeoPictures pictures,
            VimeoUser user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VimeoPictures(List<VimeoPicture> sizes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VimeoPicture(String link, Integer width) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VimeoUser(String uri, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VimeoOEmbedResponse(
            String title,
            @JsonProperty("thumbnail_url") String thumbnailUrl,
            @JsonProperty("author_name") String authorName,
            @JsonProperty("author_url") String authorUrl,
            Integer duration,
            @JsonProperty("upload_date") String uploadDate,
            @JsonProperty("video_id") Long videoId) {}
}
