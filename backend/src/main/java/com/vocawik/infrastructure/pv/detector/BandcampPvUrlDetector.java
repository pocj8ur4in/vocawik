package com.vocawik.infrastructure.pv.detector;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import com.vocawik.service.pv.detector.PvUrlDetector;
import com.vocawik.service.pv.detector.PvUrlDetectorLeaf;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Detects Bandcamp track links. */
@Component
@PvUrlDetectorLeaf
@Order(70)
public class BandcampPvUrlDetector implements PvUrlDetector {

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!isBandcampHost(parsedUrl.host())) {
            return Optional.empty();
        }
        if (parsedUrl.pathSegments().size() < 2) {
            return Optional.empty();
        }
        if (!"track".equalsIgnoreCase(parsedUrl.pathSegments().getFirst())) {
            return Optional.empty();
        }

        String videoKey = parsedUrl.pathSegments().get(1);
        if (videoKey.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(
                new DetectedPv(SongPvProvider.BANDCAMP, videoKey, parsedUrl.normalizedUrl()));
    }

    private boolean isBandcampHost(String host) {
        return host != null && (host.equals("bandcamp.com") || host.endsWith(".bandcamp.com"));
    }
}
