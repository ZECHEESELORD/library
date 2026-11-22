package sh.harold.library.tween.signal;

import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.track.StatelessSampler;

import java.util.Objects;

/**
 * A signal-driven channel (catalog 1.2): each tick a signal (a telemetry value such as speed or strafe input) is
 * read, mapped through {@code map}, and fed to a spring as its target. {@code map} is any stateless sampler whose
 * input axis is the signal value, typically a {@link sh.harold.library.tween.track.Track} keyed by signal value
 * (for example speed 0.2 maps to FOV +0, speed 0.4 maps to FOV +8).
 */
public record SignalChannelSpec(StatelessSampler<Double> map, SpringSpec spring) {

    public SignalChannelSpec {
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(spring, "spring");
    }
}
