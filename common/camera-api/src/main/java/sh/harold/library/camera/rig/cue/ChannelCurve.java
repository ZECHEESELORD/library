package sh.harold.library.camera.rig.cue;

import sh.harold.library.camera.rig.CameraChannel;

import java.util.Objects;
import java.util.Optional;

/**
 * One curve of a cue, on one channel.
 *
 * @param channel  the channel it adds to
 * @param curve    the offset over the cue's local time
 * @param reduced  the curve to play instead under the Reduced profile, taken as authored (not scaled again). Without
 *                 one, the Reduced rules scale {@code curve}
 * @param unwound  for pitch: the total may pass straight up or down (a full turn). Otherwise stage 7 keeps the
 *                 player's pitch plus this offset within [-90, 90]
 */
public record ChannelCurve(CameraChannel channel, CueCurve curve, Optional<CueCurve> reduced, boolean unwound) {

    public ChannelCurve {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(reduced, "reduced");
        if (unwound && channel != CameraChannel.PITCH) {
            throw new IllegalArgumentException("only pitch curves can be unwound");
        }
    }

    public static ChannelCurve of(CameraChannel channel, CueCurve curve) {
        return new ChannelCurve(channel, curve, Optional.empty(), false);
    }

    /** The same curve with a Reduced substitute. */
    public ChannelCurve withReduced(CueCurve substitute) {
        return new ChannelCurve(channel, curve, Optional.of(substitute), unwound);
    }

    /** The same pitch curve, allowed past straight up or down. */
    public ChannelCurve unwoundPitch() {
        return new ChannelCurve(channel, curve, reduced, true);
    }

    /** The curve the given profile plays: the substitute under Reduced when there is one. */
    public CueCurve curveFor(boolean reducedProfile) {
        return reducedProfile ? reduced.orElse(curve) : curve;
    }
}
