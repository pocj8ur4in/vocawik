package com.vocawik.service.pv.detector;

import com.vocawik.infrastructure.pv.model.DetectedPv;
import com.vocawik.infrastructure.pv.model.ParsedPvUrl;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Delegates URL detection to ordered detector units and returns the first match. */
@Component
public class ChainedPvUrlDetector implements PvUrlDetector {

    private final List<PvUrlDetector> detectors;

    public ChainedPvUrlDetector(@PvUrlDetectorLeaf List<PvUrlDetector> detectors) {
        this.detectors = List.copyOf(detectors);
    }

    @Override
    public Optional<DetectedPv> detect(ParsedPvUrl parsedUrl) {
        for (PvUrlDetector detector : detectors) {
            Optional<DetectedPv> detectedPv = detector.detect(parsedUrl);
            if (detectedPv.isPresent()) {
                return detectedPv;
            }
        }
        return Optional.empty();
    }
}
