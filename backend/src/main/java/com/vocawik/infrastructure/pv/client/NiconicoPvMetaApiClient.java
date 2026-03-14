package com.vocawik.infrastructure.pv.client;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.service.pv.client.PvApiProperties;
import com.vocawik.service.pv.client.PvMetaApiClient;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** NicoNico getthumbinfo API client for fetching PV metadata from video key. */
@Component
@RequiredArgsConstructor
public class NiconicoPvMetaApiClient implements PvMetaApiClient {

    private static final String DEFAULT_BASE_URL = "https://ext.nicovideo.jp/api/getthumbinfo";

    private final PvApiProperties pvApiProperties;
    private final PvHttpClientSupport pvHttpClientSupport;

    @Override
    public SongPvProvider provider() {
        return SongPvProvider.NICONICO;
    }

    @Override
    public PvMetaResult fetch(DetectedPv detectedPv) {
        if (detectedPv.provider() != SongPvProvider.NICONICO) {
            throw new IllegalArgumentException("unsupported provider: " + detectedPv.provider());
        }

        String url =
                buildGetThumbInfoUrl(
                        pvApiProperties.getNiconico().getBaseUrl(), detectedPv.videoKey());
        RestClient restClient = pvHttpClientSupport.createRestClient(null, null);

        String responseBody;
        try {
            responseBody =
                    pvHttpClientSupport.executeWithRetry(
                            () -> restClient.get().uri(url).retrieve().body(String.class));
        } catch (RestClientException ex) {
            throw new IllegalStateException("failed to fetch niconico pv metadata", ex);
        }

        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalStateException("niconico metadata response is empty");
        }

        Document document = parseXml(responseBody);
        Element root = document.getDocumentElement();
        if (root == null) {
            throw new IllegalStateException("niconico metadata response root is missing");
        }

        String status = nullIfBlank(root.getAttribute("status"));
        if (!"ok".equalsIgnoreCase(status)) {
            throw toFailureException(root, detectedPv.videoKey());
        }

        Element thumb = firstElement(root, "thumb");
        if (thumb == null) {
            throw new IllegalStateException("niconico metadata thumb is missing");
        }

        String title = nullIfBlank(readChildText(thumb, "title"));
        String thumbnailUrl = nullIfBlank(readChildText(thumb, "thumbnail_url"));
        String uploaderKey =
                firstNonBlank(
                        nullIfBlank(readChildText(thumb, "ch_id")),
                        nullIfBlank(readChildText(thumb, "user_id")));
        Integer durationSeconds = parseDurationSeconds(readChildText(thumb, "length"));
        String publishedAt = nullIfBlank(readChildText(thumb, "first_retrieve"));

        return new PvMetaResult(
                detectedPv.videoKey(),
                title,
                thumbnailUrl,
                uploaderKey,
                durationSeconds,
                publishedAt);
    }

    private String buildGetThumbInfoUrl(String baseUrl, String videoKey) {
        String normalizedBaseUrl = nullIfBlank(baseUrl);
        if (normalizedBaseUrl == null) {
            normalizedBaseUrl = DEFAULT_BASE_URL;
        }
        if (normalizedBaseUrl.endsWith("/")) {
            return normalizedBaseUrl + videoKey;
        }
        return normalizedBaseUrl + "/" + videoKey;
    }

    private Document parseXml(String xml) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException ex) {
            throw new IllegalStateException("failed to configure niconico xml parser", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse niconico metadata response", ex);
        }
    }

    private IllegalArgumentException toFailureException(Element root, String videoKey) {
        Element error = firstElement(root, "error");
        String code = error == null ? null : nullIfBlank(readChildText(error, "code"));
        String description =
                error == null ? null : nullIfBlank(readChildText(error, "description"));

        String message = "niconico video not found: " + videoKey;
        if (code != null && description != null) {
            message += " (" + code + ": " + description + ")";
        } else if (code != null) {
            message += " (" + code + ")";
        }
        return new IllegalArgumentException(message);
    }

    private Element firstElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        if (!(nodes.item(0) instanceof Element element)) {
            return null;
        }
        return element;
    }

    private String readChildText(Element parent, String tagName) {
        Element child = firstElement(parent, tagName);
        if (child == null) {
            return null;
        }
        return child.getTextContent();
    }

    private Integer parseDurationSeconds(String length) {
        String normalized = nullIfBlank(length);
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
