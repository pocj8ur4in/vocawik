package com.vocawik.service.pv.detector;

import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import java.util.Optional;

/** Port for resolving PV provider metadata from a raw URL. */
public interface PvUrlDetector {

    /** Attempts to detect provider metadata from parsed URL. */
    Optional<DetectedPv> detect(ParsedPvUrl parsedUrl);

    /** Attempts to detect provider metadata from raw URL. */
    default Optional<DetectedPv> detect(String rawUrl) {
        return detect(ParsedPvUrl.parse(rawUrl));
    }
}
