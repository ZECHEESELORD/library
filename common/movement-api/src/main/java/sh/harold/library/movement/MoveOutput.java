package sh.harold.library.movement;

import net.kyori.adventure.key.Key;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What the movement core asks of the host for one tick: modifiers on top of vanilla's own movement, which the host
 * then runs as usual (architecture section 5, "Movement in detail"). The host applies them in this order, before
 * {@code travel}:
 *
 * <ol>
 *     <li>{@link #jump()}: perform the host's own ground jump impulse (vanilla {@code jumpFromGround}: 0.42 up, plus
 *     the sprint boost);</li>
 *     <li>the velocity rules, per axis;</li>
 *     <li>{@link #kinematicTarget()}: set the velocity to {@code target - position}, zero the travel input and gravity,
 *     so vanilla collision and the server's movement checks still see an ordinary move;</li>
 *     <li>{@link #inputScale()} on the travel input vector;</li>
 *     <li>{@link #gravity()} replaces gravity for this tick's travel, and {@link #discardFriction()} keeps horizontal
 *     speed through it (vanilla's {@code setDiscardFriction});</li>
 *     <li>{@link #body()}: the hitbox and pose (empty means vanilla's own).</li>
 * </ol>
 *
 * <p>An output with no move live is {@link #NONE}, which changes nothing: the host behaves exactly like vanilla.
 */
public record MoveOutput(
        boolean jump,
        VelocityRule velocityX,
        VelocityRule velocityY,
        VelocityRule velocityZ,
        OptionalDouble gravity,
        Optional<Vec3> kinematicTarget,
        boolean discardFriction,
        double inputScale,
        Optional<BodyOverride> body,
        double lookScale,
        CameraSignals cameraSignals,
        List<CameraCommand> camera,
        List<MoveSound> sounds,
        List<MoveEvent> events,
        Status status
) {

    public static final MoveOutput NONE = builder().build();

    public MoveOutput {
        Objects.requireNonNull(velocityX, "velocityX");
        Objects.requireNonNull(velocityY, "velocityY");
        Objects.requireNonNull(velocityZ, "velocityZ");
        Objects.requireNonNull(gravity, "gravity");
        Objects.requireNonNull(kinematicTarget, "kinematicTarget");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(cameraSignals, "cameraSignals");
        Objects.requireNonNull(status, "status");
        camera = List.copyOf(camera);
        sounds = List.copyOf(sounds);
        events = List.copyOf(events);
        if (!(inputScale >= 0.0) || !(lookScale >= 0.0)) {
            throw new IllegalArgumentException("scales must be non-negative");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Whether this output changes nothing about the body's movement this tick. */
    public boolean passive() {
        return !jump && velocityX.mode() == VelocityRule.Mode.KEEP && velocityY.mode() == VelocityRule.Mode.KEEP
                && velocityZ.mode() == VelocityRule.Mode.KEEP && gravity.isEmpty() && kinematicTarget.isEmpty()
                && !discardFriction && inputScale == 1.0 && body.isEmpty();
    }

    /** Applies the velocity rules (not the jump impulse or the kinematic target) to {@code velocity}. */
    public Vec3 applyVelocity(Vec3 velocity) {
        return new Vec3(velocityX.apply(velocity.x()), velocityY.apply(velocity.y()), velocityZ.apply(velocity.z()));
    }

    /**
     * Additive camera targets read by signal-driven layers every tick (the slide's speed FOV, strafe roll and speed
     * shake). Zero means rest.
     */
    public record CameraSignals(double fov, double roll, double shake) {

        public static final CameraSignals REST = new CameraSignals(0.0, 0.0, 0.0);
    }

    /**
     * The live exclusive move for telemetry: its key (empty when none), ticks since its {@code T0}, and its phase
     * name. {@code verdict} is the last verdict the host recorded for it ({@code pending}, {@code accepted},
     * {@code rejected}), filled in by the host.
     */
    public record Status(Optional<Key> move, long moveTick, String phase) {

        public static final Status IDLE = new Status(Optional.empty(), -1, "idle");

        public Status {
            Objects.requireNonNull(move, "move");
            Objects.requireNonNull(phase, "phase");
        }

        public String moveId() {
            return move.map(Key::asString).orElse("");
        }
    }

    public static final class Builder {

        private boolean jump;
        private VelocityRule velocityX = VelocityRule.KEEP;
        private VelocityRule velocityY = VelocityRule.KEEP;
        private VelocityRule velocityZ = VelocityRule.KEEP;
        private OptionalDouble gravity = OptionalDouble.empty();
        private Optional<Vec3> kinematicTarget = Optional.empty();
        private boolean discardFriction;
        private double inputScale = 1.0;
        private Optional<BodyOverride> body = Optional.empty();
        private double lookScale = 1.0;
        private CameraSignals cameraSignals = CameraSignals.REST;
        private final List<CameraCommand> camera = new ArrayList<>();
        private final List<MoveSound> sounds = new ArrayList<>();
        private final List<MoveEvent> events = new ArrayList<>();
        private Status status = Status.IDLE;

        private Builder() {
        }

        public Builder jump() {
            this.jump = true;
            return this;
        }

        public boolean jumping() {
            return jump;
        }

        public Builder velocityX(VelocityRule rule) {
            this.velocityX = Objects.requireNonNull(rule, "rule");
            return this;
        }

        public Builder velocityY(VelocityRule rule) {
            this.velocityY = Objects.requireNonNull(rule, "rule");
            return this;
        }

        public Builder velocityZ(VelocityRule rule) {
            this.velocityZ = Objects.requireNonNull(rule, "rule");
            return this;
        }

        /** Sets the horizontal velocity to {@code (x, z)}. */
        public Builder horizontal(double x, double z) {
            return velocityX(VelocityRule.set(x)).velocityZ(VelocityRule.set(z));
        }

        public Builder velocity(Vec3 value) {
            return velocityX(VelocityRule.set(value.x())).velocityY(VelocityRule.set(value.y()))
                    .velocityZ(VelocityRule.set(value.z()));
        }

        public VelocityRule velocityY() {
            return velocityY;
        }

        public Builder gravity(double value) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("gravity must be finite");
            }
            this.gravity = OptionalDouble.of(value);
            return this;
        }

        public Builder kinematicTarget(Vec3 target) {
            this.kinematicTarget = Optional.of(target);
            return this;
        }

        public Builder discardFriction() {
            this.discardFriction = true;
            return this;
        }

        public Builder inputScale(double value) {
            this.inputScale = value;
            return this;
        }

        public Builder body(BodyOverride value) {
            this.body = Optional.of(value);
            return this;
        }

        public Builder lookScale(double value) {
            this.lookScale = value;
            return this;
        }

        public Builder cameraSignals(CameraSignals value) {
            this.cameraSignals = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder camera(CameraCommand command) {
            camera.add(Objects.requireNonNull(command, "command"));
            return this;
        }

        public Builder sound(MoveSound sound) {
            sounds.add(Objects.requireNonNull(sound, "sound"));
            return this;
        }

        public Builder event(MoveEvent event) {
            events.add(Objects.requireNonNull(event, "event"));
            return this;
        }

        public List<MoveEvent> events() {
            return events;
        }

        public Builder status(Status value) {
            this.status = Objects.requireNonNull(value, "value");
            return this;
        }

        public MoveOutput build() {
            return new MoveOutput(jump, velocityX, velocityY, velocityZ, gravity, kinematicTarget, discardFriction,
                    inputScale, body, lookScale, cameraSignals, camera, sounds, events, status);
        }
    }
}
