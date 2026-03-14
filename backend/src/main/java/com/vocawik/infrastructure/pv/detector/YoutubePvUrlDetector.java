package com.vocawik.infrastructure.pv.detector;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import com.vocawik.service.pv.detector.PvUrlDetector;
import com.vocawik.service.pv.detector.PvUrlDetectorLeaf;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Detects YouTube song PV links. */
@Component
@PvUrlDetectorLeaf
@Order(10)
public class YoutubePvUrlDetector implements PvUrlDetector {

    private static final Set<String> SUPPORTED_HOSTS =
            Set.of("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be");

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!SUPPORTED_HOSTS.contains(parsedUrl.host())) {
            return Optional.empty();
        }

        if ("youtu.be".equals(parsedUrl.host())) {
            if (parsedUrl.pathSegments().isEmpty()) {
                return Optional.empty();
            }
            return detected(parsedUrl.pathSegments().getFirst(), parsedUrl);
        }

        String lowerPath = parsedUrl.path().toLowerCase(Locale.ROOT);
        if ("/watch".equals(lowerPath)) {
            return detected(parsedUrl.queryParams().get("v"), parsedUrl);
        }

        if (lowerPath.startsWith("/shorts/")) {
            if (parsedUrl.pathSegments().size() < 2) {
                return Optional.empty();
            }
            return detected(parsedUrl.pathSegments().get(1), parsedUrl);
        }

        return Optional.empty();
    }

    private Optional<DetectedPv> detected(String videoKey, ParsedPvUrl parsedUrl) {
        if (videoKey == null || videoKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                new DetectedPv(SongPvProvider.YOUTUBE, videoKey, parsedUrl.normalizedUrl()));
    }
}
