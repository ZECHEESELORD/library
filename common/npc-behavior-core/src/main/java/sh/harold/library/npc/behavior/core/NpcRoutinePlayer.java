package sh.harold.library.npc.behavior.core;

import sh.harold.library.entity.EntityPose;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.NpcGesturePreset;
import sh.harold.library.npc.behavior.NpcPersonalityTuning;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcRoutineStep;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcStance;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Sequence-only routine interpreter. Timed steps are atomic; boundaries
 * between steps are safe cleanup checkpoints.
 */
public final class NpcRoutinePlayer {

    private final NpcBehaviorRenderPort renderer;
    private final NpcBehaviorRandom random;
    private final Supplier<NpcRenderFrame> baseFrame;
    private final Supplier<Vec3> actorPosition;
    private final Supplier<Optional<SpaceId>> actorSpace;
    private final Map<EquipmentSlot, ItemDescriptor> equipment = new EnumMap<>(EquipmentSlot.class);
    private final Set<EquipmentSlot> clearedEquipment = new HashSet<>();
    private final NpcGestureComposer gesture = new NpcGestureComposer();
    private NpcPersonalityTuning tuning = NpcPersonalityTuning.DEFAULT;
    private Active active;
    private Pending pendingExplicit;
    private NpcStance stance;
    private boolean usingMainHand;
    private boolean usingOffHand;
    private Look look;
    private long currentTick;

    public NpcRoutinePlayer(
            NpcBehaviorRenderPort renderer,
            NpcBehaviorRandom random,
            Supplier<NpcRenderFrame> baseFrame,
            Supplier<Vec3> actorPosition,
            Supplier<Optional<SpaceId>> actorSpace
    ) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.random = Objects.requireNonNull(random, "random");
        this.baseFrame = Objects.requireNonNull(baseFrame, "baseFrame");
        this.actorPosition = Objects.requireNonNull(actorPosition, "actorPosition");
        this.actorSpace = Objects.requireNonNull(actorSpace, "actorSpace");
    }

    public synchronized void tuning(NpcPersonalityTuning tuning) {
        this.tuning = Objects.requireNonNull(tuning, "tuning");
    }

    public synchronized boolean eligible(NpcRoutine routine) {
        Objects.requireNonNull(routine, "routine");
        for (NpcRoutineStep step : routine.steps()) {
            if (step instanceof NpcRoutineStep.LookAt lookAt && !anchorEligible(lookAt.anchor())) {
                return false;
            }
            if (step instanceof NpcRoutineStep.Sweep sweep
                    && (!anchorEligible(sweep.from()) || !anchorEligible(sweep.to()))) {
                return false;
            }
        }
        return true;
    }

    public synchronized Ticket startAmbient(NpcRoutine routine) {
        Objects.requireNonNull(routine, "routine");
        if (active != null || pendingExplicit != null || !eligible(routine)) {
            return Ticket.rejected();
        }
        Ticket ticket = new Ticket();
        active = new Active(routine, ticket, false);
        return ticket;
    }

    /** Installs/replaces an explicit routine to begin at the next checkpoint. */
    public synchronized Ticket perform(NpcRoutine routine) {
        Objects.requireNonNull(routine, "routine");
        Ticket ticket = new Ticket();
        if (pendingExplicit != null) {
            pendingExplicit.ticket.complete(false);
        }
        pendingExplicit = new Pending(routine, ticket);
        return ticket;
    }

    public synchronized void tick(long tick) {
        currentTick = tick;
        if (active == null) {
            startPendingIfEligible();
        }
        if (active == null) {
            return;
        }

        if (active.stepStarted && tick < active.stepDeadline) {
            updateTimedStep(tick);
            return;
        }

        if (active.stepStarted) {
            finishTimedStep();
            active.stepIndex++;
            active.stepStarted = false;
        }

        if (active.cancelRequested) {
            cancelActiveAtCheckpoint();
            startPendingIfEligible();
            return;
        }

        if (pendingExplicit != null) {
            cancelActiveAtCheckpoint();
            startPendingIfEligible();
            if (active == null) {
                return;
            }
        }

        while (active != null) {
            if (active.stepIndex >= active.routine.steps().size()) {
                completeActive();
                startPendingIfEligible();
                return;
            }
            NpcRoutineStep step = active.routine.steps().get(active.stepIndex);
            boolean timed = beginStep(step, tick);
            if (active == null) {
                startPendingIfEligible();
                return;
            }
            if (timed) {
                return;
            }
            active.stepIndex++;
            if (pendingExplicit != null) {
                cancelActiveAtCheckpoint();
                startPendingIfEligible();
            }
        }
    }

    public synchronized NpcRenderFrame composedFrame() {
        NpcRenderFrame base = Objects.requireNonNull(baseFrame.get(), "baseFrame returned null");
        Map<EquipmentSlot, ItemDescriptor> composedEquipment = new EnumMap<>(EquipmentSlot.class);
        composedEquipment.putAll(base.equipment());
        clearedEquipment.forEach(composedEquipment::remove);
        composedEquipment.putAll(equipment);
        EntityPose pose = switch (stance == null ? fromPose(base.pose()) : stance) {
            case STANDING -> EntityPose.STANDING;
            case CROUCHING -> EntityPose.CROUCHING;
        };
        float bodyYaw = look == null ? base.bodyYaw() : look.currentBodyYaw;
        float headYaw = look == null ? base.headYaw() : look.currentHeadYaw;
        float pitch = look == null ? base.pitch() : look.currentPitch;
        NpcRenderFrame composed = new NpcRenderFrame(
                bodyYaw,
                headYaw,
                pitch,
                pose,
                composedEquipment,
                base.usingMainHand() || usingMainHand,
                base.usingOffHand() || usingOffHand
        );
        return gesture.compose(composed, currentTick);
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                Optional.ofNullable(active).map(value -> value.routine),
                active == null ? -1 : active.stepIndex,
                active != null && active.explicit,
                Optional.ofNullable(pendingExplicit).map(value -> value.routine),
                active == null,
                composedFrame()
        );
    }

    public synchronized void cancel() {
        if (pendingExplicit != null) {
            pendingExplicit.ticket.complete(false);
            pendingExplicit = null;
        }
        gesture.clear();
        cancelActiveAtCheckpoint();
    }

    public synchronized void cancel(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        if (pendingExplicit != null && pendingExplicit.ticket == ticket) {
            pendingExplicit.ticket.complete(false);
            pendingExplicit = null;
            return;
        }
        if (active != null && active.ticket == ticket) {
            // The current timed primitive remains atomic; cancellation is
            // applied at its next cleanup checkpoint.
            active.cancelRequested = true;
        }
    }

    public synchronized boolean active() {
        return active != null;
    }

    public synchronized boolean atCleanupCheckpoint() {
        return active == null || !active.stepStarted;
    }

    private boolean beginStep(NpcRoutineStep step, long tick) {
        if (step instanceof NpcRoutineStep.LookAt lookAt) {
            AnchorSnapshot anchor = renderer.resolveAnchor(lookAt.anchor()).orElse(null);
            if (anchor == null || !sameSpace(anchor)) {
                cancelActiveAtCheckpoint();
                return false;
            }
            int duration = NpcPersonalityMotion.timingTicks(lookAt.timing(), tuning, random);
            LookAngles target = anglesTo(anchor.frame().origin());
            NpcRenderFrame base = composedFrame();
            look = Look.interpolate(base, target, tick, duration);
            beginTimed(tick, duration);
            return true;
        }
        if (step instanceof NpcRoutineStep.Sweep sweep) {
            AnchorSnapshot from = renderer.resolveAnchor(sweep.from()).orElse(null);
            AnchorSnapshot to = renderer.resolveAnchor(sweep.to()).orElse(null);
            if (from == null || to == null || !sameSpace(from) || !sameSpace(to)) {
                cancelActiveAtCheckpoint();
                return false;
            }
            int duration = NpcPersonalityMotion.timingTicks(sweep.timing(), tuning, random);
            look = Look.sweep(anglesTo(from.frame().origin()), anglesTo(to.frame().origin()), tick, duration);
            beginTimed(tick, duration);
            return true;
        }
        if (step instanceof NpcRoutineStep.Stance stanceStep) {
            stance = stanceStep.stance();
            return false;
        }
        if (step instanceof NpcRoutineStep.Equip equip) {
            equipment.put(equip.slot(), equip.item());
            clearedEquipment.remove(equip.slot());
            return false;
        }
        if (step instanceof NpcRoutineStep.EquipOneOf oneOf) {
            equipment.put(oneOf.slot(), oneOf.items().get(random.nextInt(0, oneOf.items().size())));
            clearedEquipment.remove(oneOf.slot());
            return false;
        }
        if (step instanceof NpcRoutineStep.Clear clear) {
            equipment.remove(clear.slot());
            clearedEquipment.add(clear.slot());
            return false;
        }
        if (step instanceof NpcRoutineStep.Gesture gesture) {
            NpcRenderAnimation rendered = new NpcRenderAnimation(animation(gesture.gesture()), 6);
            this.gesture.start(rendered, tick);
            renderer.animateShared(rendered);
            gesture.sound().ifPresent(this::playSharedSound);
            // Frame-backed gestures (and vanilla arm waves) remain an atomic
            // primitive until their visual beat is complete. This prevents a
            // routine from reporting a cleanup checkpoint while its authored
            // pose is still visibly in flight.
            beginTimed(tick, rendered.durationTicks());
            return true;
        }
        if (step instanceof NpcRoutineStep.Swing swing) {
            renderer.animateShared(new NpcRenderAnimation(
                    swing.hand() == InteractionHand.MAIN_HAND
                            ? NpcRenderAnimation.Type.SWING_MAIN_HAND
                            : NpcRenderAnimation.Type.SWING_OFF_HAND,
                    6
            ));
            swing.sound().ifPresent(this::playSharedSound);
            return false;
        }
        if (step instanceof NpcRoutineStep.UseItem useItem) {
            int duration = NpcPersonalityMotion.timingTicks(useItem.timing(), tuning, random);
            usingMainHand = useItem.hand() == InteractionHand.MAIN_HAND;
            usingOffHand = useItem.hand() == InteractionHand.OFF_HAND;
            renderer.animateShared(new NpcRenderAnimation(
                    usingMainHand
                            ? NpcRenderAnimation.Type.USE_MAIN_HAND
                            : NpcRenderAnimation.Type.USE_OFF_HAND,
                    duration
            ));
            useItem.sound().ifPresent(this::playSharedSound);
            beginTimed(tick, duration);
            return true;
        }
        if (step instanceof NpcRoutineStep.Sound sound) {
            playSharedSound(sound.sound());
            return false;
        }
        if (step instanceof NpcRoutineStep.Wait wait) {
            beginTimed(tick, NpcPersonalityMotion.timingTicks(wait.timing(), tuning, random));
            return true;
        }
        throw new IllegalStateException("Unsupported routine step " + step.getClass().getName());
    }

    private void beginTimed(long tick, int duration) {
        active.stepStarted = true;
        active.stepStartedAt = tick;
        active.stepDeadline = tick + duration;
    }

    private void updateTimedStep(long tick) {
        if (look != null) {
            look.update(tick);
        }
    }

    private void finishTimedStep() {
        if (look != null) {
            look.update(Long.MAX_VALUE);
        }
        usingMainHand = false;
        usingOffHand = false;
    }

    private void startPendingIfEligible() {
        if (pendingExplicit == null || active != null) {
            return;
        }
        if (!eligible(pendingExplicit.routine)) {
            return;
        }
        active = new Active(pendingExplicit.routine, pendingExplicit.ticket, true);
        pendingExplicit = null;
    }

    private void completeActive() {
        Ticket ticket = active.ticket;
        clearOverrides();
        active = null;
        ticket.complete(true);
    }

    private void cancelActiveAtCheckpoint() {
        if (active == null) {
            gesture.clear();
            clearOverrides();
            return;
        }
        Ticket ticket = active.ticket;
        clearOverrides();
        gesture.clear();
        active = null;
        ticket.complete(false);
    }

    private void clearOverrides() {
        stance = null;
        equipment.clear();
        clearedEquipment.clear();
        usingMainHand = false;
        usingOffHand = false;
        look = null;
    }

    private boolean anchorEligible(AnchorRef anchor) {
        return renderer.resolveAnchor(anchor).filter(this::sameSpace).isPresent();
    }

    private boolean sameSpace(AnchorSnapshot anchor) {
        return actorSpace.get().map(space -> space.equals(anchor.spaceId())).orElse(false);
    }

    private LookAngles anglesTo(Vec3 target) {
        Vec3 delta = target.subtract(actorPosition.get());
        double horizontal = Math.sqrt(delta.x() * delta.x() + delta.z() * delta.z());
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x(), delta.z()));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y(), Math.max(1.0e-9, horizontal)));
        pitch = Math.max(
                NpcPersonalityMotion.MAXIMUM_UP_PITCH,
                Math.min(NpcPersonalityMotion.MAXIMUM_DOWN_PITCH, pitch)
        );
        return new LookAngles(yaw, yaw, pitch);
    }

    private void playSharedSound(NpcSoundProfile profile) {
        if (profile.silent()) {
            return;
        }
        NpcSoundProfile.Variant variant = profile.variants().get(random.nextInt(0, profile.variants().size()));
        float pitch = variant.minimumPitch()
                + (float) random.nextDouble() * (variant.maximumPitch() - variant.minimumPitch());
        renderer.playSound(NpcRenderedSound.shared(variant.key(), variant.source(), variant.volume(), pitch));
    }

    private static NpcRenderAnimation.Type animation(NpcGesturePreset gesture) {
        return switch (gesture) {
            case NOD -> NpcRenderAnimation.Type.NOD;
            case HEAD_FLICK_UP -> NpcRenderAnimation.Type.HEAD_FLICK_UP;
            case HEAD_FLICK_DOWN -> NpcRenderAnimation.Type.HEAD_FLICK_DOWN;
            case WAVE -> NpcRenderAnimation.Type.WAVE;
            case DOUBLE_TAKE -> NpcRenderAnimation.Type.DOUBLE_TAKE;
            case LOOK_AROUND -> NpcRenderAnimation.Type.LOOK_AROUND;
            case CROUCH_PULSE -> NpcRenderAnimation.Type.CROUCH_PULSE;
            case LEAN_FORWARD_PROXY -> NpcRenderAnimation.Type.LEAN_FORWARD_PROXY;
            case LEAN_BACK_PROXY -> NpcRenderAnimation.Type.LEAN_BACK_PROXY;
        };
    }

    private static NpcStance fromPose(EntityPose pose) {
        return pose == EntityPose.CROUCHING ? NpcStance.CROUCHING : NpcStance.STANDING;
    }

    public record Snapshot(
            Optional<NpcRoutine> activeRoutine,
            int stepIndex,
            boolean explicit,
            Optional<NpcRoutine> pendingExplicit,
            boolean cleanupCheckpoint,
            NpcRenderFrame composedFrame
    ) {
        public Snapshot {
            activeRoutine = Objects.requireNonNull(activeRoutine, "activeRoutine");
            pendingExplicit = Objects.requireNonNull(pendingExplicit, "pendingExplicit");
            composedFrame = Objects.requireNonNull(composedFrame, "composedFrame");
        }
    }

    public static final class Ticket {
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

        private Ticket() {
        }

        private static Ticket rejected() {
            Ticket ticket = new Ticket();
            ticket.complete(false);
            return ticket;
        }

        public CompletionStage<Boolean> completion() {
            return completion;
        }

        public boolean done() {
            return completion.isDone();
        }

        private void complete(boolean naturally) {
            completion.complete(naturally);
        }
    }

    private static final class Active {
        private final NpcRoutine routine;
        private final Ticket ticket;
        private final boolean explicit;
        private int stepIndex;
        private boolean stepStarted;
        private long stepStartedAt;
        private long stepDeadline;
        private boolean cancelRequested;

        private Active(NpcRoutine routine, Ticket ticket, boolean explicit) {
            this.routine = routine;
            this.ticket = ticket;
            this.explicit = explicit;
        }
    }

    private record Pending(NpcRoutine routine, Ticket ticket) {
    }

    private record LookAngles(float bodyYaw, float headYaw, float pitch) {
    }

    private static final class Look {
        private final LookAngles from;
        private final LookAngles to;
        private final long startedAt;
        private final int duration;
        private float currentBodyYaw;
        private float currentHeadYaw;
        private float currentPitch;

        private Look(LookAngles from, LookAngles to, long startedAt, int duration) {
            this.from = from;
            this.to = to;
            this.startedAt = startedAt;
            this.duration = duration;
            update(startedAt);
        }

        private static Look interpolate(NpcRenderFrame frame, LookAngles to, long tick, int duration) {
            return new Look(new LookAngles(frame.bodyYaw(), frame.headYaw(), frame.pitch()), to, tick, duration);
        }

        private static Look sweep(LookAngles from, LookAngles to, long tick, int duration) {
            return new Look(from, to, tick, duration);
        }

        private void update(long tick) {
            double progress = tick == Long.MAX_VALUE
                    ? 1.0
                    : Math.max(0.0, Math.min(1.0, (tick - startedAt) / (double) duration));
            currentBodyYaw = interpolateAngle(from.bodyYaw, to.bodyYaw, progress);
            currentHeadYaw = interpolateAngle(from.headYaw, to.headYaw, progress);
            currentPitch = (float) (from.pitch + (to.pitch - from.pitch) * progress);
        }

        private static float interpolateAngle(float from, float to, double progress) {
            return NpcGazeController.wrapDegrees(
                    from + NpcGazeController.shortestDelta(from, to) * (float) progress
            );
        }
    }
}
