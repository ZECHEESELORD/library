package sh.harold.creative.library.curve;

import java.util.Objects;

public record CurveSplit(CurvePath leading, CurvePath trailing) {

    public CurveSplit {
        leading = Objects.requireNonNull(leading, "leading");
        trailing = Objects.requireNonNull(trailing, "trailing");
    }
}
