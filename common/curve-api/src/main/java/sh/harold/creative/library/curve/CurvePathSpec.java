package sh.harold.creative.library.curve;

import java.util.List;
import java.util.Objects;

public record CurvePathSpec(List<CurveSegmentSpec> segments) {

    public CurvePathSpec {
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("segments cannot be empty");
        }
    }
}
