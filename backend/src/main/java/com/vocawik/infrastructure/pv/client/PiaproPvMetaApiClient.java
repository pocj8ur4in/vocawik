package com.vocawik.infrastructure.pv.client;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvApiProperties;
import com.vocawik.service.pv.client.PvMetaApiClient;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** Piapro page parser for fetching song PV metadata from content URL. */
@Component
@RequiredArgsConstructor
public class PiaproPvMetaApiClient implements PvMetaApiClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
                    + " (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private static final DateTimeFormatter PIAPRO_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final DateTimeFormatter PIAPRO_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Pattern TITLE_PATTERN =
            Pattern.compile(
                    "(?is)<h1[^>]*class\\s*=\\s*['\"][^'\"]*contents_title[^'\"]*['\"][^>]*>(.*?)</h1>");
    private static final Pattern OG_TITLE_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]*property\\s*=\\s*['\"]og:title['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
    private static final Pattern AUTHOR_ID_PATTERN =
            Pattern.compile(
                    "(?is)<div[^>]*class\\s*=\\s*['\"][^'\"]*contents_creator[^'\"]*['\"][^>]*>.*?<a[^>]*href\\s*=\\s*['\"]/([^'\"/?#]+)['\"][^>]*>");
    private static final Pattern TWITTER_IMAGE_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]*name\\s*=\\s*['\"]twitter:image['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
    private static final Pattern OG_IMAGE_PATTERN =
            Pattern.compile(
                    "(?is)<meta[^>]*property\\s*=\\s*['\"]og:image['\"][^>]*content\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
    private static final Pattern DATE_PATTERN =
            Pattern.compile("投稿日\\s*[：:]\\s*(\\d{4}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})");
    private static final Pattern LENGTH_PATTERN =
            Pattern.compile("長さ\\s*[：:]\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)");
    private static final Pattern CONTENT_ID_RECOMMEND_PATTERN =
            Pattern.compile("/content_list_recommend/\\?id=([\\w\\-]+)");
    private static final Pattern CONTENT_ID_TREE_PATTERN =
            Pattern.compile("/content/tree_list/([\\w\\-]+)");
    private static final Pattern AUDIO_URL_TIMESTAMP_PATTERN =
            Pattern.compile("[\\w\\-]+_([0-9]{14})_audition\\.mp3");

    private final PvApiProperties pvApiProperties;
    private final PvHttpClientSupport pvHttpClientSupport;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.PIAPRO;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.PIAPRO) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        String requestUrl = buildRequestUrl(detectedPv);
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
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch piapro pv metadata", ex);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("piapro metadata response is empty");
        }

        String videoKey =
                firstNonBlank(extractContentId(responseBody), nullIfBlank(detectedPv.videoKey()));
        String title = extractTitle(responseBody);
        String thumbnailUrl = extractThumbnailUrl(responseBody);
        String uploaderKey = cleanText(extractFirstGroup(responseBody, AUTHOR_ID_PATTERN));
        Integer durationSeconds =
                parseDurationSeconds(extractFirstGroup(responseBody, LENGTH_PATTERN));
        String rawPublishedAt = extractFirstGroup(responseBody, DATE_PATTERN);
        String publishedAt = parsePublishedAt(rawPublishedAt);
        String timestamp = extractAudioTimestamp(rawPublishedAt, responseBody);
        String audioUrl = buildAudioUrl(videoKey, timestamp);
        PvMetaExtra extra = audioUrl == null ? null : new PvMetaExtra(audioUrl, null, null);

        return new PvMetaResult(
                videoKey, title, thumbnailUrl, uploaderKey, durationSeconds, publishedAt, extra);
    }

    private String buildRequestUrl(DetectedPv detectedPv) {
        String normalizedUrl = nullIfBlank(detectedPv.normalizedUrl());
        if (normalizedUrl != null) {
            return normalizedUrl;
        }

        String baseUrl = nullIfBlank(pvApiProperties.getPiapro().getBaseUrl());
        if (baseUrl == null) {
            baseUrl = "https://piapro.jp/t";
        }

        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(detectedPv.videoKey())
                .build(true)
                .toUriString();
    }

    private String extractTitle(String html) {
        String title = cleanText(extractFirstGroup(html, TITLE_PATTERN));
        if (title != null) {
            return title;
        }
        String ogTitle = cleanText(extractFirstGroup(html, OG_TITLE_PATTERN));
        if (ogTitle == null) {
            return null;
        }
        if (ogTitle.startsWith("[piaproオンガク]")) {
            return nullIfBlank(ogTitle.substring("[piaproオンガク]".length()));
        }
        return ogTitle;
    }

    private String extractThumbnailUrl(String html) {
        String url = cleanText(extractFirstGroup(html, TWITTER_IMAGE_PATTERN));
        if (url == null) {
            url = cleanText(extractFirstGroup(html, OG_IMAGE_PATTERN));
        }
        if (url == null) {
            return null;
        }
        if (url.startsWith("https://res.piapro.jp/images/card_chara/")) {
            return null;
        }
        return url;
    }

    private String extractContentId(String html) {
        String contentId = cleanText(extractFirstGroup(html, CONTENT_ID_RECOMMEND_PATTERN));
        if (contentId != null) {
            return contentId;
        }
        return cleanText(extractFirstGroup(html, CONTENT_ID_TREE_PATTERN));
    }

    private String extractFirstGroup(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        return matcher.group(1);
    }

    private Integer parseDurationSeconds(String rawDuration) {
        String normalized = nullIfBlank(rawDuration);
        if (normalized == null) {
            return null;
        }

        String[] parts = normalized.split(":");
        try {
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0]);
                int seconds = Integer.parseInt(parts[1]);
                return minutes * 60 + seconds;
            }
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                int seconds = Integer.parseInt(parts[2]);
                return hours * 3600 + minutes * 60 + seconds;
            }
        } catch (NumberFormatException ignored) {
            return null;
        }
        return null;
    }

    private String parsePublishedAt(String rawPublishedAt) {
        String normalized = nullIfBlank(rawPublishedAt);
        if (normalized == null) {
            return null;
        }

        try {
            LocalDateTime publishedLocal = LocalDateTime.parse(normalized, PIAPRO_DATE_FORMATTER);
            return publishedLocal.atOffset(ZoneOffset.ofHours(9)).toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String extractAudioTimestamp(String rawPublishedAt, String html) {
        String fromPublishedAt = parseTimestampFromPublishedAt(rawPublishedAt);
        if (fromPublishedAt != null) {
            return fromPublishedAt;
        }

        String source = nullIfBlank(html);
        if (source == null) {
            return null;
        }
        Matcher matcher = AUDIO_URL_TIMESTAMP_PATTERN.matcher(source);
        if (!matcher.find() || matcher.groupCount() < 1) {
            return null;
        }
        return nullIfBlank(matcher.group(1));
    }

    private String parseTimestampFromPublishedAt(String rawPublishedAt) {
        String normalized = nullIfBlank(rawPublishedAt);
        if (normalized == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(normalized, PIAPRO_DATE_FORMATTER)
                    .format(PIAPRO_TIMESTAMP_FORMATTER);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String buildAudioUrl(String videoKey, String timestamp) {
        String normalizedVideoKey = nullIfBlank(videoKey);
        String normalizedTimestamp = nullIfBlank(timestamp);
        if (normalizedVideoKey == null || normalizedTimestamp == null) {
            return null;
        }
        if (normalizedVideoKey.length() < 2) {
            return null;
        }
        String prefix = normalizedVideoKey.substring(0, 2);
        return "https://cdn.piapro.jp/mp3_a/"
                + prefix
                + "/"
                + normalizedVideoKey
                + "_"
                + normalizedTimestamp
                + "_audition.mp3";
    }

    private String cleanText(String value) {
        String normalized = nullIfBlank(value);
        if (normalized == null) {
            return null;
        }
        String withoutTags = normalized.replaceAll("(?is)<[^>]+>", " ");
        String unescaped = HtmlUtils.htmlUnescape(withoutTags);
        return nullIfBlank(unescaped.replaceAll("\\s+", " ").trim());
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
}
