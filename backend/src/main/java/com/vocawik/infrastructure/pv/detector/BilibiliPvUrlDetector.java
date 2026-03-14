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

/** Detects Bilibili song PV links. */
@Component
@PvUrlDetectorLeaf
@Order(30)
public class BilibiliPvUrlDetector implements PvUrlDetector {

    private static final Set<String> SUPPORTED_HOSTS = Set.of("bilibili.com", "m.bilibili.com");

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!SUPPORTED_HOSTS.contains(parsedUrl.host())) {
            return Optional.empty();
        }
        if (parsedUrl.pathSegments().size() < 2) {
            return Optional.empty();
        }
        if (!"video".equalsIgnoreCase(parsedUrl.pathSegments().getFirst())) {
            return Optional.empty();
        }

        String videoKey = parsedUrl.pathSegments().get(1);
        String lowerVideoKey = videoKey.toLowerCase(Locale.ROOT);
        if (!(lowerVideoKey.startsWith("av") || lowerVideoKey.startsWith("bv"))) {
            return Optional.empty();
        }
        return Optional.of(
                new DetectedPv(SongPvProvider.BILIBILI, videoKey, parsedUrl.normalizedUrl()));
    }
}
