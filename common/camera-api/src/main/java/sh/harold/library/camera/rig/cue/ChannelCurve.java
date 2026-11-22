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
 * @param absolute for eye height: the curve is the drawn eye height above the feet, not an offset. See
 *                 {@link #absoluteEyeHeight()}
 */
public record ChannelCurve(CameraChannel channel, CueCurve curve, Optional<CueCurve> reduced, boolean unwound,
                           boolean absolute) {

    public ChannelCurve {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(curve, "curve");
        Objects.requireNonNull(reduced, "reduced");
        if (unwound && channel != CameraChannel.PITCH) {
            throw new IllegalArgumentException("only pitch curves can be unwound");
        }
        if (absolute && channel != CameraChannel.EYE_HEIGHT) {
            throw new IllegalArgumentException("only eye height curves can be absolute");
        }
    }

    public static ChannelCurve of(CameraChannel channel, CueCurve curve) {
        return new ChannelCurve(channel, curve, Optional.empty(), false, false);
    }

    /** The same curve with a Reduced substitute. */
    public ChannelCurve withReduced(CueCurve substitute) {
        return new ChannelCurve(channel, curve, Optional.of(substitute), unwound, absolute);
    }

    /** The same pitch curve, allowed past straight up or down. */
    public ChannelCurve unwoundPitch() {
        return new ChannelCurve(channel, curve, reduced, true, absolute);
    }

    /**
     * The same eye-height curve as an absolute target: its value is the drawn eye height above the feet, whatever the
     * host's own camera is doing. Each frame the rig turns it into the offset from the host's eased eye height
     * ({@link sh.harold.library.camera.rig.ViewInput#eyeHeight()}), and while it is live the host adapter holds its
     * own eye-height easing at the body's eye, so the host's camera has nothing left to catch up on when the curve
     * ends. The latest-started live absolute curve wins; additive eye-height curves still add on top. A release ends
     * an absolute curve at once (there is no offset to fade), and the Reduced profile plays it as authored.
     */
    public ChannelCurve absoluteEyeHeight() {
        return new ChannelCurve(channel, curve, reduced, unwound, true);
    }

    /** The curve the given profile plays: the substitute under Reduced when there is one. */
    public CueCurve curveFor(boolean reducedProfile) {
        return reducedProfile ? reduced.orElse(curve) : curve;
    }
}
