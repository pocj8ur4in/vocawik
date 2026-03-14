package com.vocawik.infrastructure.pv.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvApiProperties;
import com.vocawik.service.pv.client.PvMetaApiClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** YouTube Data API client for fetching PV metadata from video key. */
@Component
@RequiredArgsConstructor
public class YoutubePvMetaApiClient implements PvMetaApiClient {

    private static final String YOUTUBE_VIDEOS_PATH = "/videos";

    private final PvApiProperties pvApiProperties;
    private final PvHttpClientSupport pvHttpClientSupport;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.YOUTUBE;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.YOUTUBE) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        PvApiProperties.Youtube youtube = pvApiProperties.getYoutube();
        if (youtube.getApiKey() == null || youtube.getApiKey().isBlank()) {
            throw new IllegalStateException("pv.youtube.api-key must be configured");
        }

        String url =
                buildVideosApiUrl(youtube.getBaseUrl(), detectedPv.videoKey(), youtube.getApiKey());
        RestClient restClient =
                pvHttpClientSupport.createRestClient(
                        youtube.getConnectTimeoutMs(), youtube.getReadTimeoutMs());

        YoutubeVideosResponse responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(url)
                                            .retrieve()
                                            .body(YoutubeVideosResponse.class));
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch youtube pv metadata", ex);
        }

        if (responseBody == null) {
            throw new IllegalStateException("youtube metadata response is empty");
        }
        if (responseBody.items() == null || responseBody.items().isEmpty()) {
            throw new IllegalArgumentException("youtube video not found: " + detectedPv.videoKey());
        }

        YoutubeVideoItem item = responseBody.items().getFirst();

        YoutubeSnippet snippet = item.snippet();
        YoutubeContentDetails contentDetails = item.contentDetails();

        String title = snippet == null ? null : nullIfBlank(snippet.title());
        String thumbnailUrl =
                snippet == null ? null : extractBestThumbnailUrl(snippet.thumbnails());
        String uploaderKey = snippet == null ? null : nullIfBlank(snippet.channelId());
        Integer durationSeconds =
                parseDurationSeconds(contentDetails == null ? null : contentDetails.duration());
        String publishedAt = snippet == null ? null : nullIfBlank(snippet.publishedAt());

        return new PvMetaResult(
                detectedPv.videoKey(),
                title,
                thumbnailUrl,
                uploaderKey,
                durationSeconds,
                publishedAt);
    }

    private String buildVideosApiUrl(String baseUrl, String videoKey, String apiKey) {
        String normalizedBaseUrl = baseUrl == null || baseUrl.isBlank() ? "" : baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        return UriComponentsBuilder.fromUriString(normalizedBaseUrl)
                .path(YOUTUBE_VIDEOS_PATH)
                .queryParam("part", "snippet,contentDetails")
                .queryParam("id", videoKey)
                .queryParam("key", apiKey)
                .build(true)
                .toUriString();
    }

    private String extractBestThumbnailUrl(Map<String, YoutubeThumbnail> thumbnails) {
        if (thumbnails == null || thumbnails.isEmpty()) {
            return null;
        }

        String[] priority = {"maxres", "standard", "high", "medium", "default"};
        for (String sizeKey : priority) {
            YoutubeThumbnail sizeNode = thumbnails.get(sizeKey);
            String url = sizeNode == null ? null : nullIfBlank(sizeNode.url());
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private Integer parseDurationSeconds(String isoDuration) {
        String value = nullIfBlank(isoDuration);
        if (value == null) {
            return null;
        }

        try {
            return Math.toIntExact(Duration.parse(value).getSeconds());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YoutubeVideosResponse(List<YoutubeVideoItem> items) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YoutubeVideoItem(YoutubeSnippet snippet, YoutubeContentDetails contentDetails) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YoutubeSnippet(
            String title,
            Map<String, YoutubeThumbnail> thumbnails,
            String channelId,
            String publishedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YoutubeThumbnail(String url) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record YoutubeContentDetails(String duration) {}
}
