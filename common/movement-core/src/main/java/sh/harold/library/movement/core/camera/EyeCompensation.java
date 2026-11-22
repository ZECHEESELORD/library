package sh.harold.library.movement.core.camera;

import sh.harold.library.tween.track.StatelessSampler;

import java.util.Arrays;

/**
 * Turns a designed absolute eye height into an eye-height cue on top of vanilla's own camera.
 *
 * <p>Vanilla's camera eases its eye height toward the body's: each tick, before the body moves, it does
 * {@code eye += (bodyEye - eye) * 0.5} with the body's eye height from the end of the previous tick, and frames lerp
 * between the last two values. In cue time (local time {@code k + 1} is the end of move tick {@code Tk}) that camera
 * shows {@code c(0) = c(1) = start} and {@code c(j + 1) = c(j) + (s(j - 1) - c(j)) / 2}, where {@code s(k)} is the
 * body's eye height at the end of {@code Tk}, linear between whole ticks. The cue is the designed height minus that,
 * so the camera shows exactly the designed curve while the hitbox does whatever the move needs.
 */
public final class EyeCompensation {

    /** Converged when the vanilla camera is this close to the body's final eye height. */
    public static final double EPSILON = 1.0e-3;

    private EyeCompensation() {
    }

    /**
     * Vanilla's camera eye at whole cue times {@code 0..length}.
     *
     * @param startEye the camera's eye height before the move (settled)
     * @param bodyEye  {@code s(k)} for {@code k = 0, 1, ...}; the last value holds after the array ends
     */
    public static double[] cameraModel(double startEye, double[] bodyEye, int length) {
        double[] camera = new double[Math.max(2, length + 1)];
        camera[0] = startEye;
        camera[1] = startEye;
        for (int j = 1; j + 1 < camera.length; j++) {
            double body = bodyEye.length == 0 ? startEye : bodyEye[Math.min(j - 1, bodyEye.length - 1)];
            camera[j + 1] = camera[j] + (body - camera[j]) * 0.5;
        }
        return camera;
    }

    /** How long until vanilla's camera settles on the schedule's last eye height, and the designed curve has ended. */
    public static int settleTicks(double startEye, double[] bodyEye, double designedEnd) {
        double target = bodyEye.length == 0 ? startEye : bodyEye[bodyEye.length - 1];
        double[] camera = cameraModel(startEye, bodyEye, bodyEye.length + 64);
        for (int j = Math.max((int) Math.ceil(designedEnd), bodyEye.length + 1); j < camera.length; j++) {
            if (Math.abs(camera[j] - target) < EPSILON) {
                return j;
            }
        }
        return camera.length - 1;
    }

    /** The cue: {@code designed(t) - c(t)}. Past the model's end the camera is taken as settled. */
    public static StatelessSampler<Double> cue(StatelessSampler<Double> designed, double[] camera) {
        double[] model = Arrays.copyOf(camera, camera.length);
        return time -> designed.sample(time) - sample(model, time);
    }

    static double sample(double[] model, double time) {
        if (time <= 0.0) {
            return model[0];
        }
        int index = (int) Math.floor(time);
        if (index >= model.length - 1) {
            return model[model.length - 1];
        }
        double u = time - index;
        return model[index] + (model[index + 1] - model[index]) * u;
    }
}
