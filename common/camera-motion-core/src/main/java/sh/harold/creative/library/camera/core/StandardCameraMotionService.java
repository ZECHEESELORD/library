package sh.harold.creative.library.camera.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.camera.BlendMode;
import sh.harold.creative.library.camera.CameraAxis;
import sh.harold.creative.library.camera.CameraDelta;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotionPlayback;
import sh.harold.creative.library.camera.CameraMotionService;
import sh.harold.creative.library.camera.Envelope;
import sh.harold.creative.library.camera.Waveform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StandardCameraMotionService implements CameraMotionService {

    private static final double EPSILON = 1.0e-9;
    private static final double TAU = Math.PI * 2.0;
    private static final double LN_2 = Math.log(2.0);

    private final Map<UUID, ControllerState> controllers = new ConcurrentHashMap<>();

    private volatile boolean closed;

    @Override
    public CameraMotionPlayback start(UUID viewerId, CameraMotion motion) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(motion, "motion");
        ensureOpen();

        ControllerState controller = controllers.computeIfAbsent(viewerId, ignored -> new ControllerState());
        controller.start(motion);
        return new StandardPlayback(this, viewerId, motion.key());
    }

    @Override
    public boolean stop(UUID viewerId, Key key) {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(key, "key");
        ControllerState controller = controllers.get(viewerId);
        if (controller == null) {
            return false;
        }
        boolean stopped = controller.stop(key);
        if (controller.isIdle()) {
            controllers.remove(viewerId, controller);
        }
        return stopped;
    }

    @Override
    public void stopAll(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        ControllerState controller = controllers.get(viewerId);
        if (controller == null) {
            return;
        }
        controller.stopAll();
        if (controller.isIdle()) {
            controllers.remove(viewerId, controller);
        }
    }

    public Collection<UUID> activeViewers() {
        return List.copyOf(controllers.keySet());
    }

    public CameraDelta tick(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        if (closed) {
            return CameraDelta.none();
        }
        ControllerState controller = controllers.get(viewerId);
        if (controller == null) {
            return CameraDelta.none();
        }
        CameraDelta delta = controller.tick();
        if (controller.isIdle()) {
            controllers.remove(viewerId, controller);
        }
        return delta;
    }

    public void discardViewer(UUID viewerId) {
        Objects.requireNonNull(viewerId, "viewerId");
        ControllerState controller = controllers.remove(viewerId);
        if (controller != null) {
            controller.discard();
        }
    }

    @Override
    public void close() {
        closed = true;
        controllers.values().forEach(ControllerState::discard);
        controllers.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Camera motion service is closed");
        }
    }

    private static double sampleMotion(CameraAxis axis, Envelope envelope, long ageTicks) {
        if (axis.amplitudeDegrees() == 0.0) {
            return 0.0;
        }
        return axis.amplitudeDegrees() * sampleWaveform(axis, ageTicks) * sampleEnvelope(envelope, ageTicks);
    }

    private static double sampleWaveform(CameraAxis axis, long ageTicks) {
        Waveform waveform = axis.waveform();
        if (waveform == Waveform.IMPULSE) {
            return 1.0;
        }
        double cycles = (ageTicks + axis.phaseTicks()) / (double) axis.periodTicks();
        double x = cycles * TAU;
        return switch (waveform) {
            case SINE -> Math.sin(x);
            case COSINE -> Math.cos(x);
            case TRIANGLE -> (2.0 / Math.PI) * Math.asin(Math.sin(x));
            case SAW -> (2.0 * wrapUnit(cycles)) - 1.0;
            case NOISE -> sampleNoise(cycles, axis.seed());
            case IMPULSE -> 1.0;
        };
    }

    private static double sampleEnvelope(Envelope envelope, long ageTicks) {
        if (ageTicks < 0L || ageTicks >= envelope.durationTicks()) {
            return 0.0;
        }
        double value = switch (envelope) {
            case Envelope.Constant constant -> constant.strength();
            case Envelope.LinearDecay linear -> lerp(linear.startStrength(), linear.endStrength(),
                    ageTicks / (double) linear.durationTicks());
            case Envelope.ExponentialDecay exponential -> exponential.startStrength() * Math.exp(-exponential.lambda() * ageTicks);
            case Envelope.ExponentialHalfLife halfLife ->
                    halfLife.startStrength() * Math.exp(-(LN_2 / halfLife.halfLifeTicks()) * ageTicks);
            case Envelope.AttackHoldRelease attackHoldRelease -> sampleAttackHoldRelease(attackHoldRelease, ageTicks);
            case Envelope.EaseOut easeOut -> easeOut.startStrength()
                    * easeOut.curve().apply(clamp01(1.0 - (ageTicks / (double) easeOut.durationTicks())));
        };
        return clamp01(value);
    }

    private static double sampleAttackHoldRelease(Envelope.AttackHoldRelease envelope, long ageTicks) {
        long tick = ageTicks;
        if (envelope.attackTicks() > 0L && tick < envelope.attackTicks()) {
            return envelope.peakStrength() * ((tick + 1.0) / envelope.attackTicks());
        }
        tick -= envelope.attackTicks();
        if (tick < envelope.holdTicks()) {
            return envelope.peakStrength();
        }
        tick -= envelope.holdTicks();
        if (envelope.releaseTicks() > 0L && tick < envelope.releaseTicks()) {
            return envelope.peakStrength() * (1.0 - (tick / (double) envelope.releaseTicks()));
        }
        return 0.0;
    }

    private static double sampleNoise(double cycles, long seed) {
        long start = (long) Math.floor(cycles);
        double local = cycles - start;
        double from = hashToRange(seed, start);
        double to = hashToRange(seed, start + 1L);
        double smoothed = local * local * (3.0 - (2.0 * local));
        return lerp(from, to, smoothed);
    }

    private static double hashToRange(long seed, long value) {
        long mixed = seed + (value * 0x9E3779B97F4A7C15L);
        mixed ^= mixed >>> 30;
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        return (((mixed >>> 11) * 0x1.0p-53) * 2.0) - 1.0;
    }

    private static double wrapUnit(double value) {
        return value - Math.floor(value);
    }

    private static double lerp(double start, double end, double progress) {
        return start + ((end - start) * progress);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private static boolean effectivelyZero(double value) {
        return Math.abs(value) < EPSILON;
    }

    private static final class StandardPlayback implements CameraMotionPlayback {

        private final StandardCameraMotionService service;
        private final UUID viewerId;
        private final Key key;

        private StandardPlayback(StandardCameraMotionService service, UUID viewerId, Key key) {
            this.service = service;
            this.viewerId = viewerId;
            this.key = key;
        }

        @Override
        public UUID viewerId() {
            return viewerId;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public void stop() {
            service.stop(viewerId, key);
        }
    }

    private static final class ControllerState {

        private final Map<Key, ActiveMotion> motions = new LinkedHashMap<>();

        private double pendingYawCompensation;
        private double pendingPitchCompensation;
        private boolean discarded;

        synchronized void start(CameraMotion motion) {
            ActiveMotion replaced = motions.remove(motion.key());
            if (replaced != null) {
                queueCompensation(replaced);
            }
            motions.put(motion.key(), new ActiveMotion(motion));
            discarded = false;
        }

        synchronized boolean stop(Key key) {
            ActiveMotion removed = motions.remove(key);
            if (removed == null) {
                return false;
            }
            queueCompensation(removed);
            return true;
        }

        synchronized void stopAll() {
            motions.values().forEach(this::queueCompensation);
            motions.clear();
        }

        synchronized CameraDelta tick() {
            if (discarded) {
                return CameraDelta.none();
            }

            double addYaw = 0.0;
            double addPitch = 0.0;

            double maxYaw = 0.0;
            double maxPitch = 0.0;
            ActiveMotion maxYawMotion = null;
            ActiveMotion maxPitchMotion = null;

            List<Key> expired = new ArrayList<>();
            for (ActiveMotion motion : motions.values()) {
                MotionDelta delta = motion.tick();
                if (motion.motion().blendMode() == BlendMode.ADD) {
                    addYaw += delta.yaw();
                    addPitch += delta.pitch();
                    motion.appliedYawContribution += delta.yaw();
                    motion.appliedPitchContribution += delta.pitch();
                } else {
                    if (Math.abs(delta.yaw()) > Math.abs(maxYaw)) {
                        maxYaw = delta.yaw();
                        maxYawMotion = motion;
                    }
                    if (Math.abs(delta.pitch()) > Math.abs(maxPitch)) {
                        maxPitch = delta.pitch();
                        maxPitchMotion = motion;
                    }
                }
                if (motion.expired()) {
                    expired.add(motion.motion().key());
                }
            }

            if (maxYawMotion != null) {
                maxYawMotion.appliedYawContribution += maxYaw;
            }
            if (maxPitchMotion != null) {
                maxPitchMotion.appliedPitchContribution += maxPitch;
            }

            double yaw = pendingYawCompensation + addYaw + maxYaw;
            double pitch = pendingPitchCompensation + addPitch + maxPitch;
            pendingYawCompensation = 0.0;
            pendingPitchCompensation = 0.0;

            for (Key key : expired) {
                ActiveMotion removed = motions.remove(key);
                if (removed != null) {
                    queueCompensation(removed);
                }
            }

            if (effectivelyZero(yaw) && effectivelyZero(pitch)) {
                return CameraDelta.none();
            }
            return new CameraDelta(yaw, pitch);
        }

        synchronized void discard() {
            discarded = true;
            motions.clear();
            pendingYawCompensation = 0.0;
            pendingPitchCompensation = 0.0;
        }

        synchronized boolean isIdle() {
            return motions.isEmpty() && effectivelyZero(pendingYawCompensation) && effectivelyZero(pendingPitchCompensation);
        }

        private void queueCompensation(ActiveMotion motion) {
            if (!effectivelyZero(motion.appliedYawContribution)) {
                pendingYawCompensation -= motion.appliedYawContribution;
                motion.appliedYawContribution = 0.0;
            }
            if (!effectivelyZero(motion.appliedPitchContribution)) {
                pendingPitchCompensation -= motion.appliedPitchContribution;
                motion.appliedPitchContribution = 0.0;
            }
        }
    }

    private static final class ActiveMotion {

        private final CameraMotion motion;

        private long ageTicks;
        private double previousYawSample;
        private double previousPitchSample;
        private double appliedYawContribution;
        private double appliedPitchContribution;

        private ActiveMotion(CameraMotion motion) {
            this.motion = motion;
        }

        CameraMotion motion() {
            return motion;
        }

        MotionDelta tick() {
            double yawSample = sampleMotion(motion.yaw(), motion.envelope(), ageTicks);
            double pitchSample = sampleMotion(motion.pitch(), motion.envelope(), ageTicks);

            double yawDelta = yawSample - previousYawSample;
            double pitchDelta = pitchSample - previousPitchSample;

            previousYawSample = yawSample;
            previousPitchSample = pitchSample;
            ageTicks++;
            return new MotionDelta(yawDelta, pitchDelta);
        }

        boolean expired() {
            return ageTicks >= motion.envelope().durationTicks();
        }
    }

    private record MotionDelta(double yaw, double pitch) {
    }
}
