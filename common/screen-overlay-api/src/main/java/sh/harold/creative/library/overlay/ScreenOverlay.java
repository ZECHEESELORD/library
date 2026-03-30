package sh.harold.creative.library.overlay;

import net.kyori.adventure.text.format.TextColor;

import java.time.Duration;
import java.util.Objects;

public record ScreenOverlay(
        TextColor color,
        float opacity,
        Duration fadeIn,
        Duration hold,
        Duration fadeOut,
        OverlayConflictPolicy conflictPolicy
) {

    public ScreenOverlay {
        color = Objects.requireNonNull(color, "color");
        fadeIn = requireNonNegative(fadeIn, "fadeIn");
        hold = requireNonNegative(hold, "hold");
        fadeOut = requireNonNegative(fadeOut, "fadeOut");
        conflictPolicy = Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException("opacity must be between 0.0 and 1.0");
        }
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Duration value = Objects.requireNonNull(duration, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " cannot be negative");
        }
        return value;
    }
}
