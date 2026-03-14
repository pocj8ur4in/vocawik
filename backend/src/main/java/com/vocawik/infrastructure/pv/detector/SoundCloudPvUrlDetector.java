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

/** Detects SoundCloud track links. */
@Component
@PvUrlDetectorLeaf
@Order(50)
public class SoundCloudPvUrlDetector implements PvUrlDetector {

    private static final Set<String> SUPPORTED_HOSTS = Set.of("soundcloud.com", "m.soundcloud.com");

    private static final Set<String> RESERVED_FIRST_SEGMENTS =
            Set.of(
                    "discover",
                    "charts",
                    "stream",
                    "upload",
                    "you",
                    "settings",
                    "search",
                    "stations",
                    "genres");

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!SUPPORTED_HOSTS.contains(parsedUrl.host())) {
            return Optional.empty();
        }
        if (parsedUrl.pathSegments().size() != 2) {
            return Optional.empty();
        }

        String artistKey = parsedUrl.pathSegments().getFirst();
        String trackKey = parsedUrl.pathSegments().get(1);
        if (artistKey.isBlank() || trackKey.isBlank()) {
            return Optional.empty();
        }
        if (RESERVED_FIRST_SEGMENTS.contains(artistKey.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        if ("sets".equalsIgnoreCase(trackKey)) {
            return Optional.empty();
        }

        String videoKey = artistKey + "/" + trackKey;
        return Optional.of(
                new DetectedPv(SongPvProvider.SOUNDCLOUD, videoKey, parsedUrl.normalizedUrl()));
    }
}
