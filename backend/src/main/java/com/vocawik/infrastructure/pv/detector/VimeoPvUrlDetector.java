package com.vocawik.infrastructure.pv.detector;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import com.vocawik.service.pv.detector.PvUrlDetector;
import com.vocawik.service.pv.detector.PvUrlDetectorLeaf;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Detects Vimeo song PV links. */
@Component
@PvUrlDetectorLeaf
@Order(60)
public class VimeoPvUrlDetector implements PvUrlDetector {

    private static final Set<String> SUPPORTED_HOSTS =
            Set.of("vimeo.com", "player.vimeo.com", "m.vimeo.com");

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!SUPPORTED_HOSTS.contains(parsedUrl.host())) {
            return Optional.empty();
        }

        String videoKey = extractVideoKey(parsedUrl.pathSegments());
        if (videoKey == null) {
            return Optional.empty();
        }

        return Optional.of(
                new DetectedPv(SongPvProvider.VIMEO, videoKey, parsedUrl.normalizedUrl()));
    }

    private String extractVideoKey(List<String> segments) {
        if (segments.isEmpty()) {
            return null;
        }

        for (String segment : segments) {
            if (isDigits(segment)) {
                return segment;
            }
        }

        if (segments.size() >= 2 && "video".equalsIgnoreCase(segments.getFirst())) {
            String candidate = segments.get(1);
            if (isDigits(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isDigits(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
