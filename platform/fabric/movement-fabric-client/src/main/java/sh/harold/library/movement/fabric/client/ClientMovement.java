package sh.harold.library.movement.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.kyori.adventure.key.Key;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import sh.harold.library.camera.fabric.client.FabricClientCamera;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.CameraService;
import sh.harold.library.camera.rig.cue.CameraCueHandle;
import sh.harold.library.tick.KeyedHandle;
import sh.harold.library.movement.Aabb;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.CameraCommand;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MoveSound;
import sh.harold.library.movement.MoveVerdict;
import sh.harold.library.movement.MovementService;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.MovementEngine;
import sh.harold.library.movement.core.MovementPredictor;
import sh.harold.library.movement.core.MovementSimulation;
import sh.harold.library.movement.core.MovementState;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.movement.core.tuning.MovesetSpec;
import sh.harold.library.movement.fabric.BodyOverrides;
import sh.harold.library.movement.fabric.MinecraftBodies;
import sh.harold.library.movement.fabric.net.MovementPayloads;
import sh.harold.library.net.fabric.client.ClientReceivers;
import sh.harold.library.prediction.ErrorSmoothingSpec;
import sh.harold.library.prediction.core.ErrorSmoother;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.telemetry.ColumnHandle;
import sh.harold.library.telemetry.TelemetryRegistry;
import sh.harold.library.telemetry.fabric.client.ClientFrame;
import sh.harold.library.telemetry.fabric.client.FabricClientTelemetry;
import sh.harold.library.tween.signal.SignalChannelSpec;
import sh.harold.library.tween.spring.SpringSpec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The client half of movement. Each tick it reads vanilla's input, runs the movement core predictively on the local
 * player, applies the output inside vanilla's tick (see the mixins), sends claims for started moves, and reconciles
 * the server's verdicts: a rejected claim replays the recorded inputs without the move ({@link MovementPredictor}),
 * moves the body there and smooths the camera across the gap instead of snapping. Camera cues go through the open
 * {@link FabricClientCamera} service (opened here when none is), so the Full or Reduced setting applies.
 *
 * <p>The moveset comes from the server. Until one arrives, and on a server without the movement mod, the moveset is
 * empty and every hook is a no-op: the client is exactly vanilla. Open with {@link #open()}; one is open at a time.
 * Use from the client thread.
 */
public final class ClientMovement implements MovementService {

    /** Ticks a rejected move stays forbidden after the verdict, so the replay does not start it again. */
    public static final int REJECT_VETO_TICKS = 20;

    private static final AtomicBoolean EVENTS = new AtomicBoolean();
    private static volatile ClientMovement active;

    private final ClientReceivers receivers;
    private final List<ColumnHandle> columns = new ArrayList<>();
    private final List<KeyedHandle> signalLayers = new ArrayList<>();
    private final Map<Key, CueRecord> cues = new HashMap<>();
    private final Map<Integer, PendingClaim> pending = new HashMap<>();
    private final Map<Key, Integer> liveSeq = new HashMap<>();
    private final Deque<InputFrame> simulated = new ArrayDeque<>();
    private final ErrorSmoother smoother = new ErrorSmoother(ErrorSmoothingSpec.DEFAULT);
    private MovesetSpec spec = MovesetSpec.none();
    private MovementEngine engine;
    private MovementPredictor predictor;
    private MovementState state = new MovementState();
    private MoveOutput output = MoveOutput.NONE;
    private InputFrame input = InputFrame.EMPTY;
    /** Ticks the jump key has been down in the current press. */
    private int jumpHeldTicks;
    /** J3: the length in ticks of the press released this tick, 0 on every other tick. */
    private int jumpPressTicks;
    private MoveVerdict lastVerdict;
    private CameraService boundCamera;
    private CameraService ownedCamera;
    private long tick;
    private int seq;
    private boolean savedDiscardFriction;
    private boolean closed;

    private ClientMovement() {
        rebuild();
        receivers = ClientReceivers.forFeature(MovementPayloads.acquire())
                .onPlay(MovementPayloads.MovesetPayload.TYPE, (payload, context) -> applySpec(payload.spec()))
                .onPlay(MovementPayloads.VerdictPayload.TYPE, (payload, context) -> onVerdict(payload.verdict()));
        registerColumns(FabricClientTelemetry.registry());
    }

    /**
     * Opens the client service. Open it during client init so the movement feature is in this client's handshake.
     *
     * @throws IllegalStateException if one is already open
     */
    public static synchronized ClientMovement open() {
        if (active != null && !active.closed) {
            throw new IllegalStateException("a client movement service is already open; close it first");
        }
        installEvents();
        ClientMovement service = new ClientMovement();
        active = service;
        return service;
    }

    public static Optional<ClientMovement> current() {
        ClientMovement service = active;
        return service == null || service.closed ? Optional.empty() : Optional.of(service);
    }

    static void installEvents() {
        if (!EVENTS.compareAndSet(false, true)) {
            return;
        }
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> current().ifPresent(
                service -> service.applySpec(MovesetSpec.none())));
    }

    // ---- MovementService (local prediction only; the server's service decides what is enabled) ----

    @Override
    public Set<Key> enabledMoves() {
        return engineMoveset().enabled();
    }

    /** Changes this client's prediction only, until the server next sends its moveset. */
    @Override
    public void setEnabled(Key move, boolean enabled) {
        applySpec(spec.withMove(move, enabled));
    }

    @Override
    public JumpPreset jumpPreset() {
        return engineMoveset().jump().preset();
    }

    @Override
    public void setJumpPreset(JumpPreset preset) {
        applySpec(spec.withJumpPreset(preset));
    }

    @Override
    public SlideVariant slideVariant() {
        return engineMoveset().slide().variant();
    }

    @Override
    public void setSlideVariant(SlideVariant variant) {
        applySpec(spec.withSlideVariant(variant));
    }

    @Override
    public boolean active() {
        return !closed;
    }

    public MovesetSpec spec() {
        return spec;
    }

    /** The output of the newest tick. */
    public MoveOutput lastOutput() {
        return output;
    }

    /** The input the engine read on the newest tick. */
    public InputFrame lastInput() {
        return input;
    }

    public MoveOutput.Status status() {
        return output.status();
    }

    public Optional<MoveVerdict> lastVerdict() {
        return Optional.ofNullable(lastVerdict);
    }

    /** Claims sent and not yet answered. */
    public int pendingClaims() {
        return pending.size();
    }

    // ---- test hook ----

    /**
     * Plays {@code frames} as the local player's input, one per client tick from the next tick, in place of the
     * keyboard; the keyboard returns when the script ends or the handle closes. Replaces any script still playing.
     */
    public InputScript simulateInput(List<InputFrame> frames) {
        simulated.clear();
        simulated.addAll(frames);
        return new InputScript(this);
    }

    /** As {@link #simulateInput(List)}, from {@code "FS*15 FSN*30 -*5"}: letters as in {@link InputFrame#keys}, {@code *n} repeats. */
    public InputScript simulateInput(String script) {
        return simulateInput(InputScript.parse(script));
    }

    int simulatedRemaining() {
        return simulated.size();
    }

    void stopSimulation() {
        simulated.clear();
    }

    public static InputFrame nextSimulatedFrame() {
        ClientMovement service = active;
        return service == null || service.closed ? null : service.simulated.poll();
    }

    // ---- hooks from the mixins ----

    private static ClientMovement managing(LivingEntity entity) {
        ClientMovement service = active;
        if (service == null || service.closed || service.engine.empty() || !(entity instanceof LocalPlayer player)) {
            return null;
        }
        return Minecraft.getInstance().player == player ? service : null;
    }

    public static void beforeAiStep(LivingEntity entity) {
        ClientMovement service = managing(entity);
        if (service != null) {
            service.step((LocalPlayer) entity);
        }
    }

    public static boolean ownsJump(LivingEntity entity) {
        ClientMovement service = managing(entity);
        return service != null && service.engine.has(MoveKeys.JUMP);
    }

    public static void beforeTravel(LivingEntity entity) {
        ClientMovement service = managing(entity);
        if (service == null) {
            return;
        }
        MoveOutput out = service.output;
        if (out.cancelSprint()) {
            entity.setSprinting(false);
        }
        if (out.jump()) {
            ((LocalPlayer) entity).jumpFromGround();
        }
        net.minecraft.world.phys.Vec3 v = entity.getDeltaMovement();
        double vx = out.velocityX().apply(v.x);
        double vy = out.velocityY().apply(v.y);
        double vz = out.velocityZ().apply(v.z);
        boolean discard = out.discardFriction();
        if (out.kinematicTarget().isPresent()) {
            Vec3 delta = out.kinematicTarget().get().subtract(MinecraftBodies.vec(entity.position()));
            vx = delta.x();
            vy = delta.y();
            vz = delta.z();
            discard = true;
        }
        entity.setDeltaMovement(vx, vy, vz);
        service.savedDiscardFriction = entity.shouldDiscardFriction();
        if (discard) {
            entity.setDiscardFriction(true);
        }
    }

    public static net.minecraft.world.phys.Vec3 travelInput(LivingEntity entity, net.minecraft.world.phys.Vec3 input) {
        ClientMovement service = managing(entity);
        if (service == null) {
            return input;
        }
        double scale = service.output.kinematicTarget().isPresent() ? 0.0 : service.output.inputScale();
        return scale == 1.0 ? input : input.multiply(scale, 1.0, scale);
    }

    public static void afterTravel(LivingEntity entity) {
        ClientMovement service = managing(entity);
        if (service != null) {
            entity.setDiscardFriction(service.savedDiscardFriction);
            service.record((LocalPlayer) entity);
        }
    }

    public static Double gravity(LivingEntity entity) {
        ClientMovement service = managing(entity);
        if (service == null) {
            return null;
        }
        if (service.output.kinematicTarget().isPresent()) {
            return 0.0;
        }
        return service.output.gravity().isPresent() ? service.output.gravity().getAsDouble() : null;
    }

    public static Vec3 cameraOffset(float partial) {
        ClientMovement service = active;
        if (service == null || service.closed) {
            return null;
        }
        Vec3 offset = service.smoother.offset(partial);
        return offset.lengthSquared() < 1.0e-10 ? null : offset;
    }

    // ---- the tick ----

    private void step(LocalPlayer player) {
        tick++;
        smoother.tick();
        InputFrame previous = input;
        input = MinecraftBodies.input(player.input.keyPresses);
        if (input.jump()) {
            jumpHeldTicks++;
            jumpPressTicks = 0;
        } else {
            jumpPressTicks = previous.jump() ? jumpHeldTicks : 0;
            jumpHeldTicks = 0;
        }
        BodyState body = MinecraftBodies.body(player);
        MovementWorld world = MinecraftBodies.world(player.level());
        output = engine.step(state, body, input, world, tick);
        BodyOverrides.set(player, output.body().orElse(null));
        for (MoveEvent event : output.events()) {
            switch (event) {
                case MoveEvent.Started started -> claim(started.claim());
                case MoveEvent.Ended ended -> end(ended);
                case MoveEvent.FallDistanceReset ignored -> player.resetFallDistance();
                case MoveEvent.FallDistanceCap cap -> player.fallDistance = Math.min(player.fallDistance, cap.blocks());
                case MoveEvent.Particles particles -> particles(player, particles);
                default -> {
                }
            }
        }
        for (CameraCommand command : output.camera()) {
            camera(command);
        }
        for (MoveSound sound : output.sounds()) {
            sound(player, sound);
        }
    }

    private void record(LocalPlayer player) {
        predictor.record(tick, input,
                new MovementSimulation.Snapshot(MinecraftBodies.body(player), state.copy(), 0, output));
    }

    private void claim(MoveClaim claim) {
        int id = ++seq;
        pending.put(id, new PendingClaim(claim.move(), tick));
        liveSeq.put(claim.move(), id);
        if (ClientPlayNetworking.canSend(MovementPayloads.ClaimPayload.TYPE)) {
            ClientPlayNetworking.send(new MovementPayloads.ClaimPayload(claim.withSeq(id)));
        } else {
            pending.remove(id); // single-player without a server service, or a server without movement
        }
    }

    private void end(MoveEvent.Ended ended) {
        Integer id = liveSeq.remove(ended.move());
        if (id != null && ClientPlayNetworking.canSend(MovementPayloads.EndPayload.TYPE)) {
            ClientPlayNetworking.send(new MovementPayloads.EndPayload(new MoveEnd(id, ended.tick(), ended.reason())));
        }
    }

    private void onVerdict(MoveVerdict verdict) {
        lastVerdict = verdict;
        PendingClaim claim = pending.remove(verdict.seq());
        if (claim == null || verdict.accepted() || closed) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (Objects.equals(liveSeq.get(claim.move()), verdict.seq())) {
            liveSeq.remove(claim.move());
        }
        for (CueRecord record : cues.values()) {
            if (record.startTick >= claim.startTick && record.handle.active()) {
                record.handle.release();
            }
        }
        MovementWorld world = MinecraftBodies.world(player.level());
        var replay = predictor.reject(claim.move(), claim.startTick, tick + REJECT_VETO_TICKS, world);
        if (replay.isPresent()) {
            MovementSimulation.Snapshot now = replay.get().current();
            state = now.movement().copy();
            Vec3 from = MinecraftBodies.vec(player.position());
            Vec3 to = now.body().position();
            boolean fits = world.free(Aabb.ofBody(to, player.getBbWidth(), now.body().height()).inflate(-1.0e-4));
            if (fits && from.distance(to) < ErrorSmoothingSpec.DEFAULT.snapDistance()) {
                player.setPos(to.x(), to.y(), to.z());
                player.setDeltaMovement(MinecraftBodies.vec(now.body().velocity()));
                smoother.absorb(from, to);
            }
        } else {
            state.veto(claim.move(), tick + REJECT_VETO_TICKS);
            state.dropActive(claim.move());
        }
        if (state.active().isEmpty()) {
            BodyOverrides.set(player, null);
        }
    }

    // ---- camera, sound, particles ----

    private CameraService camera() {
        Optional<CameraService> open = FabricClientCamera.active().filter(CameraService::active);
        CameraService service;
        if (open.isPresent()) {
            service = open.get();
        } else {
            ownedCamera = FabricClientCamera.open();
            service = ownedCamera;
        }
        if (service != boundCamera) {
            signalLayers.forEach(KeyedHandle::close);
            signalLayers.clear();
            boundCamera = service;
            SignalChannelSpec identity = new SignalChannelSpec(signal -> signal, SpringSpec.critical(6.0));
            signalLayers.add(service.addSignalLayer(Key.key(MoveKeys.NAMESPACE, "signal/fov"), CameraChannel.FOV,
                    identity, () -> output.cameraSignals().fov()));
            signalLayers.add(service.addSignalLayer(Key.key(MoveKeys.NAMESPACE, "signal/roll"), CameraChannel.ROLL,
                    identity, () -> output.cameraSignals().roll()));
            signalLayers.add(service.addSignalLayer(Key.key(MoveKeys.NAMESPACE, "signal/shake"), CameraChannel.SHAKE,
                    identity, () -> output.cameraSignals().shake()));
        }
        return service;
    }

    private void camera(CameraCommand command) {
        CameraService service = camera();
        switch (command) {
            case CameraCommand.Start start -> {
                CueRecord previous = cues.remove(start.cue().key());
                if (previous != null) {
                    previous.handle.cancel();
                }
                // Cue local time k + 1 is the end of move tick Tk, so the cue starts one rig tick back.
                CameraCueHandle handle = service.start(start.cue(), service.now() - 1.0);
                cues.put(start.cue().key(), new CueRecord(handle, tick));
            }
            case CameraCommand.Release release -> {
                CueRecord record = cues.get(release.key());
                if (record != null) {
                    record.handle.release();
                }
            }
            case CameraCommand.Cancel cancel -> {
                CueRecord record = cues.remove(cancel.key());
                if (record != null) {
                    record.handle.cancel();
                }
            }
        }
        cues.values().removeIf(record -> !record.handle.active());
    }

    private static void sound(LocalPlayer player, MoveSound sound) {
        ClientLevel level = (ClientLevel) player.level();
        SoundEvent event = null;
        if (sound.source() != MoveSound.Source.EVENT) {
            BlockPos pos = BlockPos.containing(sound.at().x(), sound.at().y() - 0.2, sound.at().z());
            BlockState block = level.getBlockState(pos);
            if (!block.isAir()) {
                event = sound.source() == MoveSound.Source.BLOCK_STEP ? block.getSoundType().getStepSound()
                        : block.getSoundType().getHitSound();
            }
        }
        if (event == null) {
            Identifier id = Identifier.tryParse(sound.soundId());
            event = id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        }
        if (event != null) {
            level.playLocalSound(sound.at().x(), sound.at().y(), sound.at().z(), event, SoundSource.PLAYERS,
                    sound.volume(), sound.pitch(), false);
        }
    }

    private static void particles(LocalPlayer player, MoveEvent.Particles particles) {
        ClientLevel level = (ClientLevel) player.level();
        Vec3 at = particles.at();
        BlockState wall = null;
        BlockPos feet = BlockPos.containing(at.x(), at.y() + 1.0, at.z());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState state = level.getBlockState(feet.relative(direction));
            if (!state.isAir()) {
                wall = state;
                break;
            }
        }
        for (int i = 0; i < 3; i++) {
            double x = at.x() + (player.getRandom().nextDouble() - 0.5) * 0.6;
            double y = at.y() + 0.6 + player.getRandom().nextDouble() * 0.8;
            double z = at.z() + (player.getRandom().nextDouble() - 0.5) * 0.6;
            if (wall != null) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, wall), x, y, z, 0.0, -0.05, 0.0);
            } else {
                level.addParticle(ParticleTypes.POOF, x, y, z, 0.0, 0.0, 0.0);
            }
        }
    }

    // ---- moveset ----

    private void applySpec(MovesetSpec next) {
        spec = Objects.requireNonNull(next, "next");
        rebuild();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            BodyOverrides.set(player, null);
        }
    }

    private void rebuild() {
        engine = MovementEngine.of(spec.moveset());
        predictor = new MovementPredictor(new MovementSimulation(engine));
        state = new MovementState();
        output = MoveOutput.NONE;
        pending.clear();
        liveSeq.clear();
        smoother.clear();
    }

    private Moveset engineMoveset() {
        return spec.moveset();
    }

    private void registerColumns(TelemetryRegistry<ClientFrame> registry) {
        columns.add(registry.registerText("move.id", frame -> output.status().move().map(Key::asString).orElse("")));
        columns.add(registry.registerLong("move.tick", frame -> output.status().moveTick()));
        columns.add(registry.registerText("move.phase", frame -> output.status().phase()));
        columns.add(registry.registerText("move.input", frame -> input.compact()));
        columns.add(registry.registerText("move.verdict", frame -> lastVerdict == null ? ""
                : (lastVerdict.accepted() ? "accept " : "reject ") + lastVerdict.seq()
                + (lastVerdict.accepted() ? "" : " " + lastVerdict.reason())));
        columns.add(registry.registerLong("move.pending", frame -> pending.size()));
        columns.add(registry.registerDouble("body.height", frame -> frame.player().getBbHeight()));
        columns.add(registry.registerDouble("body.fall_distance", frame -> frame.player().fallDistance));
        columns.add(registry.registerLong("move.jump_press_ticks", frame -> jumpPressTicks));
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        receivers.close();
        columns.forEach(ColumnHandle::close);
        columns.clear();
        signalLayers.forEach(KeyedHandle::close);
        signalLayers.clear();
        for (CueRecord record : cues.values()) {
            record.handle.cancel();
        }
        cues.clear();
        if (ownedCamera != null) {
            ownedCamera.close();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            BodyOverrides.set(player, null);
        }
        simulated.clear();
        MovementPayloads.release();
    }

    private record PendingClaim(Key move, long startTick) {
    }

    private record CueRecord(CameraCueHandle handle, long startTick) {
    }
}
