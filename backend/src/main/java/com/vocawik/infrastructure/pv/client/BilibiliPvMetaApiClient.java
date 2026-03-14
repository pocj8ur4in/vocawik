package com.vocawik.infrastructure.pv.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvApiProperties;
import com.vocawik.service.pv.client.PvMetaApiClient;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/** Bilibili API client for fetching PV metadata from video key. */
@Component
@RequiredArgsConstructor
public class BilibiliPvMetaApiClient implements PvMetaApiClient {

    private static final String DEFAULT_BASE_URL = "https://api.bilibili.com/x/web-interface/view";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private final PvApiProperties pvApiProperties;
    private final PvHttpClientSupport pvHttpClientSupport;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.BILIBILI;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.BILIBILI) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        String videoKey = detectedPv.videoKey();
        String requestUrl =
                buildViewApiUrl(
                        pvApiProperties.getBilibili().getBaseUrl(), normalizeVideoKey(videoKey));
        RestClient restClient = pvHttpClientSupport.createRestClient(null, null);

        BilibiliViewResponse responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () ->
                                    restClient
                                            .get()
                                            .uri(requestUrl)
                                            .header("User-Agent", USER_AGENT)
                                            .header("Referer", "https://www.bilibili.com")
                                            .retrieve()
                                            .body(BilibiliViewResponse.class));
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch bilibili pv metadata", ex);
        }

        if (responseBody == null) {
            throw new IllegalStateException("bilibili metadata response is empty");
        }
        if (responseBody.code() != 0 || responseBody.data() == null) {
            String message = nullIfBlank(responseBody.message());
            if (message == null) {
                message = "unknown";
            }
            throw new IllegalArgumentException(
                    "bilibili video not found: "
                            + videoKey
                            + " (code="
                            + responseBody.code()
                            + ", message="
                            + message
                            + ")");
        }

        BilibiliData data = responseBody.data();
        String normalizedVideoKey = firstNonBlank(nullIfBlank(data.bvid()), videoKey);

        return new PvMetaResult(
                normalizedVideoKey,
                nullIfBlank(data.title()),
                nullIfBlank(data.pic()),
                toUploaderKey(data.owner()),
                toDurationSeconds(data.duration()),
                toPublishedAt(data.pubdate()));
    }

    private String buildViewApiUrl(String baseUrl, NormalizedVideoKey normalizedVideoKey) {
        String normalizedBaseUrl = nullIfBlank(baseUrl);
        if (normalizedBaseUrl == null) {
            normalizedBaseUrl = DEFAULT_BASE_URL;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(normalizedBaseUrl);
        if (normalizedVideoKey.isBvid()) {
            builder.queryParam("bvid", normalizedVideoKey.value());
        } else {
            builder.queryParam("aid", normalizedVideoKey.value());
        }
        return builder.build(true).toUriString();
    }

    private NormalizedVideoKey normalizeVideoKey(String videoKey) {
        String normalized = nullIfBlank(videoKey);
        if (normalized == null) {
            throw new IllegalArgumentException("videoKey is required");
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("av")) {
            if (normalized.length() <= 2) {
                throw new IllegalArgumentException("invalid bilibili av key: " + videoKey);
            }
            return new NormalizedVideoKey(false, normalized.substring(2));
        }
        if (lower.startsWith("bv")) {
            return new NormalizedVideoKey(true, normalized);
        }
        throw new IllegalArgumentException("invalid bilibili key: " + videoKey);
    }

    private String toUploaderKey(BilibiliOwner owner) {
        if (owner == null || owner.mid() == null) {
            return null;
        }
        return String.valueOf(owner.mid());
    }

    private Integer toDurationSeconds(Integer duration) {
        if (duration == null || duration < 0) {
            return null;
        }
        return duration;
    }

    private String toPublishedAt(Long pubdate) {
        if (pubdate == null || pubdate <= 0L) {
            return null;
        }
        return Instant.ofEpochSecond(pubdate).toString();
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
    private record BilibiliViewResponse(int code, String message, BilibiliData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BilibiliData(
            String bvid,
            String title,
            String pic,
            Integer duration,
            Long pubdate,
            BilibiliOwner owner) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BilibiliOwner(Long mid) {}

    private record NormalizedVideoKey(boolean isBvid, String value) {}
}
