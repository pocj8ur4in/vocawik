package com.vocawik.infrastructure.pv.detector;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import com.vocawik.service.pv.detector.PvUrlDetector;
import com.vocawik.service.pv.detector.PvUrlDetectorLeaf;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Detects Piapro song PV links. */
@Component
@PvUrlDetectorLeaf
@Order(40)
public class PiaproPvUrlDetector implements PvUrlDetector {

    private static final Set<String> SUPPORTED_HOSTS = Set.of("piapro.jp");

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!SUPPORTED_HOSTS.contains(parsedUrl.host())) {
            return Optional.empty();
        }
        if (parsedUrl.pathSegments().size() < 2
                || !"t".equalsIgnoreCase(parsedUrl.pathSegments().getFirst())) {
            return Optional.empty();
        }
        String videoKey = parsedUrl.pathSegments().get(1);
        if (videoKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(
                new DetectedPv(SongPvProvider.PIAPRO, videoKey, parsedUrl.normalizedUrl()));
    }
}
