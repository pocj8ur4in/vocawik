package com.vocawik.infrastructure.pv.detector;

import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import com.vocawik.service.pv.detector.PvUrlDetector;
import com.vocawik.service.pv.detector.PvUrlDetectorLeaf;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Detects NicoNico song PV links. */
@Component
@PvUrlDetectorLeaf
@Order(20)
public class NiconicoPvUrlDetector implements PvUrlDetector {

    private static final Set<String> SUPPORTED_HOSTS =
            Set.of("nicovideo.jp", "sp.nicovideo.jp", "nico.ms");

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("(?i)^(sm|nm|so)\\d+$");

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        if (!SUPPORTED_HOSTS.contains(parsedUrl.host())) {
            return Optional.empty();
        }
        if (parsedUrl.pathSegments().isEmpty()) {
            return Optional.empty();
        }

        String videoKey;
        if ("nico.ms".equals(parsedUrl.host())) {
            videoKey = parsedUrl.pathSegments().getFirst();
        } else {
            if (parsedUrl.pathSegments().size() < 2
                    || !"watch".equalsIgnoreCase(parsedUrl.pathSegments().getFirst())) {
                return Optional.empty();
            }
            videoKey = parsedUrl.pathSegments().get(1);
        }

        if (!VIDEO_ID_PATTERN.matcher(videoKey).matches()) {
            return Optional.empty();
        }
        return Optional.of(
                new DetectedPv(SongPvProvider.NICONICO, videoKey, parsedUrl.normalizedUrl()));
    }
}
