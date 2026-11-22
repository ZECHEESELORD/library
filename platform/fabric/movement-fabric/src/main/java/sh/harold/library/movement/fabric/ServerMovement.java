package sh.harold.library.movement.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kyori.adventure.key.Key;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.ClaimCheck;
import sh.harold.library.movement.ClaimContext;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.MoveVerdict;
import sh.harold.library.movement.MovementService;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.ClaimValidator;
import sh.harold.library.movement.core.MovementEngine;
import sh.harold.library.movement.core.tuning.JsonText;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.movement.core.tuning.MovesetSpec;
import sh.harold.library.movement.fabric.net.MovementPayloads;
import sh.harold.library.net.fabric.NetFeature;
import sh.harold.library.reload.HotReloadService;
import sh.harold.library.reload.core.StandardHotReloadService;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.telemetry.ColumnHandle;
import sh.harold.library.telemetry.TelemetryRegistry;
import sh.harold.library.telemetry.fabric.FabricServerTelemetry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The server half of movement: it pushes the moveset to clients, checks their move claims with the shared
 * {@link ClaimValidator}, and applies what an accepted claim changes on the server (the roll's fall damage reduction,
 * the roll and slide hitboxes, wall kicks resetting fall distance). Open with {@link #open()} or
 * {@link #open(Path)}; one service is open at a time. Closing it clears every override and returns the server to
 * vanilla. Use from the server thread.
 *
 * <p>The race fix: a roll or slide claim lowers the server hitbox as soon as it arrives, before it is decided, so the
 * client's next movement packet (which already moves the low body under a gap) is not rejected as moving wrongly. A
 * rejection restores the hitbox.
 */
public final class ServerMovement implements MovementService {

    private static final Logger LOG = Logger.getLogger(ServerMovement.class.getName());
    private static final AtomicBoolean HOOKS = new AtomicBoolean();
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);
    private static volatile ServerMovement active;

    @SuppressWarnings("unused") // held so the movement feature stays declared while this service is open
    private final NetFeature feature;
    private final HotReloadService reload;
    private final List<ColumnHandle> columns = new ArrayList<>();
    private final Map<UUID, Mover> movers = new HashMap<>();
    private MovesetSpec spec;
    private Moveset moveset;
    private MovementEngine engine;
    private ClaimValidator validator;
    private MinecraftServer server;
    private long tick;
    private boolean closed;

    private ServerMovement(MovesetSpec spec, Path tuningDir) {
        this.spec = spec;
        rebuild();
        this.feature = MovementPayloads.acquire();
        registerColumns(FabricServerTelemetry.registry());
        this.reload = tuningDir == null ? null : watch(tuningDir);
    }

    /** Opens the service with every stock move at default tuning and no hot reload. */
    public static ServerMovement open() {
        return open(MovesetSpec.DEFAULT, null);
    }

    /**
     * Opens the service and hot-reloads the tuning files in {@code tuningDir} (written from the defaults when
     * missing). A file that fails to decode keeps its last good value and logs the file, key and field.
     */
    public static ServerMovement open(Path tuningDir) {
        return open(MovesetSpec.DEFAULT, Objects.requireNonNull(tuningDir, "tuningDir"));
    }

    public static synchronized ServerMovement open(MovesetSpec spec, Path tuningDir) {
        Objects.requireNonNull(spec, "spec");
        if (active != null && !active.closed) {
            throw new IllegalStateException("a server movement service is already open; close it first");
        }
        installHooks();
        ServerMovement service = new ServerMovement(spec, tuningDir);
        active = service;
        return service;
    }

    public static Optional<ServerMovement> current() {
        ServerMovement service = active;
        return service == null || service.closed ? Optional.empty() : Optional.of(service);
    }

    /** Registers the Fabric events once per JVM; they route to the open service and do nothing without one. */
    public static void installHooks() {
        if (!HOOKS.compareAndSet(false, true)) {
            return;
        }
        MovementPayloads.registerTypes();
        ServerTickEvents.END_SERVER_TICK.register(server -> current().ifPresent(service -> service.tick(server)));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> current().ifPresent(service -> {
            service.server = server;
            service.push(handler.player);
        }));
        // Receivers are global and route to the open service, so closing one never unregisters what another
        // service (or the client half in single player) relies on. The payload types are registered at mod init.
        ServerPlayNetworking.registerGlobalReceiver(MovementPayloads.ClaimPayload.TYPE,
                (payload, context) -> current().ifPresent(service -> service.onClaim(context.player(), payload.claim())));
        ServerPlayNetworking.registerGlobalReceiver(MovementPayloads.EndPayload.TYPE,
                (payload, context) -> current().ifPresent(service -> service.onEnd(context.player(), payload.end())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> current().ifPresent(
                service -> service.forget(handler.player)));
    }

    // ---- MovementService ----

    @Override
    public Set<Key> enabledMoves() {
        return moveset.enabled();
    }

    @Override
    public void setEnabled(Key move, boolean enabled) {
        update(spec.withMove(move, enabled));
    }

    @Override
    public JumpPreset jumpPreset() {
        return moveset.jump().preset();
    }

    @Override
    public void setJumpPreset(JumpPreset preset) {
        update(spec.withJumpPreset(preset));
    }

    @Override
    public SlideVariant slideVariant() {
        return moveset.slide().variant();
    }

    @Override
    public void setSlideVariant(SlideVariant variant) {
        update(spec.withSlideVariant(variant));
    }

    @Override
    public boolean active() {
        return !closed;
    }

    public MovesetSpec spec() {
        return spec;
    }

    public Moveset moveset() {
        return moveset;
    }

    /** The newest verdict sent to {@code player}, for tests. */
    public Optional<MoveVerdict> lastVerdict(ServerPlayer player) {
        Mover mover = movers.get(player.getUUID());
        return mover == null ? Optional.empty() : Optional.ofNullable(mover.lastVerdict);
    }

    /** Replaces the whole spec and pushes it to every client. */
    public void update(MovesetSpec next) {
        ensureOpen();
        MovesetSpec previous = spec;
        spec = Objects.requireNonNull(next, "next");
        try {
            rebuild();
        } catch (RuntimeException exception) {
            spec = previous;
            rebuild();
            throw exception;
        }
        pushAll();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (reload != null) {
            reload.close();
        }
        columns.forEach(ColumnHandle::close);
        columns.clear();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BodyOverrides.set(player, null);
                Mover mover = movers.get(player.getUUID());
                if (mover != null && mover.pendingFall != null) {
                    applyFall(player, mover.pendingFall, 0.0);
                }
                if (ServerPlayNetworking.canSend(player, MovementPayloads.MovesetPayload.TYPE)) {
                    ServerPlayNetworking.send(player, new MovementPayloads.MovesetPayload(MovesetSpec.none()));
                }
            }
        }
        movers.clear();
        MovementPayloads.release();
    }

    // ---- fall damage ----

    /**
     * Called by the fall damage mixin. Returns null to let vanilla run, or the value to return instead when the
     * damage is held for the roll window.
     */
    public static Boolean interceptFall(LivingEntity entity, double fallDistance, float multiplier, DamageSource source) {
        if (BYPASS.get() || !(entity instanceof ServerPlayer player)) {
            return null;
        }
        ServerMovement service = active;
        if (service == null || service.closed) {
            return null;
        }
        return service.onFall(player, fallDistance, multiplier, source);
    }

    private Boolean onFall(ServerPlayer player, double fallDistance, float multiplier, DamageSource source) {
        Mover mover = mover(player);
        mover.landingFall = fallDistance;
        mover.landingTick = tick;
        if (!moveset.enables(MoveKeys.ROLL) || fallDistance + 0.75 < moveset.roll().minFall()) {
            return null;
        }
        if (mover.pendingFall != null) {
            applyFall(player, mover.pendingFall, 0.0);
        }
        long hold = moveset.roll().windowAfter() + moveset.core().latencyGraceTicks() + 1L;
        mover.pendingFall = new PendingFall(fallDistance, multiplier, source, tick + hold);
        return false;
    }

    private void applyFall(ServerPlayer player, PendingFall fall, double reduction) {
        mover(player).pendingFall = null;
        double distance = fall.distance() - reduction;
        if (distance <= 0.0) {
            return;
        }
        BYPASS.set(true);
        try {
            player.causeFallDamage(distance, fall.multiplier(), fall.source());
        } finally {
            BYPASS.set(false);
        }
    }

    // ---- claims ----

    private void onClaim(ServerPlayer player, MoveClaim claim) {
        if (closed) {
            return;
        }
        server = player.level().getServer();
        Mover mover = mover(player);
        mover.observe(player, tick);
        BodyOverride low = lowBody(claim.move());
        if (low != null) {
            setBody(player, mover, claim.seq(), low); // race fix: lower first, decide after
        }
        Pending pending = new Pending(claim);
        if (!decide(player, mover, pending)) {
            mover.pending.add(pending);
        }
    }

    private boolean decide(ServerPlayer player, Mover mover, Pending pending) {
        ClaimCheck check = validator.check(pending.claim, new Context(player, mover, pending.waited));
        switch (check.verdict()) {
            case ACCEPT -> {
                accept(player, mover, pending.claim);
                send(player, mover, MoveVerdict.accept(pending.claim.seq()));
                return true;
            }
            case REJECT -> {
                if (mover.bodySeq == pending.claim.seq()) {
                    setBody(player, mover, -1, null);
                }
                send(player, mover, MoveVerdict.reject(pending.claim.seq(), check.reason()));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void accept(ServerPlayer player, Mover mover, MoveClaim claim) {
        Key move = claim.move();
        mover.liveMove = move;
        mover.liveSeq = claim.seq();
        mover.liveSince = tick;
        if (move.equals(MoveKeys.ROLL)) {
            mover.bodyUntil = tick + moveset.roll().ticks() + 3L;
            if (mover.pendingFall != null) {
                applyFall(player, mover.pendingFall, moveset.roll().damageReduction());
            }
        } else if (move.equals(MoveKeys.SLIDE)) {
            mover.bodyUntil = tick + 100L;
        } else if (move.equals(MoveKeys.WALL_JUMP)) {
            player.resetFallDistance();
            mover.lastKick = claim.normal();
        } else if (move.equals(MoveKeys.WALL_SLIDE) || move.equals(MoveKeys.MANTLE)) {
            player.resetFallDistance();
        }
    }

    private void onEnd(ServerPlayer player, MoveEnd end) {
        if (closed) {
            return;
        }
        Mover mover = mover(player);
        if (mover.bodySeq == end.seq()) {
            setBody(player, mover, -1, null);
        }
        if (mover.liveSeq == end.seq()) {
            if (MoveKeys.WALL_SLIDE.equals(mover.liveMove)) {
                player.resetFallDistance();
            }
            mover.liveMove = null;
            mover.liveSeq = -1;
        }
        mover.pending.removeIf(pending -> pending.claim.seq() == end.seq()
                && end.reason() == MoveEnd.EndReason.REJECTED);
    }

    private BodyOverride lowBody(Key move) {
        if (move.equals(MoveKeys.ROLL) && moveset.enables(MoveKeys.ROLL)) {
            return new BodyOverride(moveset.roll().height(), moveset.roll().eyeHeight(), BodyOverride.RenderPose.CROUCHING);
        }
        if (move.equals(MoveKeys.SLIDE) && moveset.enables(MoveKeys.SLIDE)) {
            return new BodyOverride(moveset.slide().height(), moveset.slide().eyeHeight(), BodyOverride.RenderPose.CRAWLING);
        }
        return null;
    }

    private void setBody(ServerPlayer player, Mover mover, int seq, BodyOverride override) {
        mover.bodySeq = override == null ? -1 : seq;
        if (override != null && mover.bodyUntil < tick + 20L) {
            mover.bodyUntil = tick + 20L; // until decided; accept extends it
        }
        BodyOverrides.set(player, override);
    }

    private void send(ServerPlayer player, Mover mover, MoveVerdict verdict) {
        mover.lastVerdict = verdict;
        if (ServerPlayNetworking.canSend(player, MovementPayloads.VerdictPayload.TYPE)) {
            ServerPlayNetworking.send(player, new MovementPayloads.VerdictPayload(verdict));
        }
    }

    // ---- ticking ----

    private void tick(MinecraftServer server) {
        this.server = server;
        tick = server.getTickCount();
        if (reload != null && tick % 20 == 0) {
            reload.poll();
            reload.applyPending();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Mover mover = movers.get(player.getUUID());
            if (mover == null) {
                continue;
            }
            mover.observe(player, tick);
            for (Iterator<Pending> it = mover.pending.iterator(); it.hasNext(); ) {
                Pending pending = it.next();
                pending.waited++;
                if (decide(player, mover, pending)) {
                    it.remove();
                }
            }
            if (mover.bodySeq >= 0 && tick >= mover.bodyUntil) {
                setBody(player, mover, -1, null);
            }
            if (mover.pendingFall != null && tick >= mover.pendingFall.applyAt()) {
                applyFall(player, mover.pendingFall, 0.0);
            }
        }
    }

    private void push(ServerPlayer player) {
        movers.computeIfAbsent(player.getUUID(), id -> new Mover());
        if (ServerPlayNetworking.canSend(player, MovementPayloads.MovesetPayload.TYPE)) {
            ServerPlayNetworking.send(player, new MovementPayloads.MovesetPayload(spec));
        }
    }

    private void pushAll() {
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(this::push);
        }
    }

    private void forget(ServerPlayer player) {
        movers.remove(player.getUUID());
        BodyOverrides.set(player, null);
    }

    private Mover mover(ServerPlayer player) {
        return movers.computeIfAbsent(player.getUUID(), id -> new Mover());
    }

    private void rebuild() {
        Moveset next = spec.moveset();
        moveset = next;
        engine = MovementEngine.of(next);
        validator = new ClaimValidator(engine);
    }

    private HotReloadService watch(Path dir) {
        try {
            Files.createDirectories(dir);
            for (String file : Moveset.FILES) {
                Path path = dir.resolve(file);
                if (Files.notExists(path)) {
                    Files.writeString(path, Moveset.defaultText(file));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        HotReloadService service = new StandardHotReloadService(error -> LOG.warning(error.toString()));
        for (String file : Moveset.FILES) {
            service.watch(dir.resolve(file), root -> {
                String text = JsonText.write(root);
                spec.withText(file, text).moveset(); // throws with the file, key and field when bad
                return text;
            }, Moveset.defaultText(file), text -> {
                spec = spec.withText(file, text);
                rebuild();
                pushAll();
            });
        }
        return service;
    }

    private void registerColumns(TelemetryRegistry<ServerPlayer> registry) {
        columns.add(registry.registerText("move.id", player -> {
            Mover mover = movers.get(player.getUUID());
            return mover == null || mover.liveMove == null ? "" : mover.liveMove.asString();
        }));
        columns.add(registry.registerLong("move.tick", player -> {
            Mover mover = movers.get(player.getUUID());
            return mover == null || mover.liveMove == null ? -1L : tick - mover.liveSince;
        }));
        columns.add(registry.registerText("move.verdict", player -> {
            Mover mover = movers.get(player.getUUID());
            MoveVerdict verdict = mover == null ? null : mover.lastVerdict;
            return verdict == null ? "" : (verdict.accepted() ? "accept " : "reject ") + verdict.seq()
                    + (verdict.accepted() ? "" : " " + verdict.reason());
        }));
        columns.add(registry.registerLong("move.pending", player -> {
            Mover mover = movers.get(player.getUUID());
            return mover == null ? 0L : mover.pending.size();
        }));
        columns.add(registry.registerDouble("body.height", ServerPlayer::getBbHeight));
        columns.add(registry.registerDouble("body.fall_distance", player -> player.fallDistance));
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("the server movement service is closed");
        }
    }

    private record PendingFall(double distance, float multiplier, DamageSource source, long applyAt) {
    }

    private static final class Pending {
        final MoveClaim claim;
        int waited;

        Pending(MoveClaim claim) {
            this.claim = claim;
        }
    }

    /** One player's server-side movement record. */
    private static final class Mover {
        final List<Pending> pending = new ArrayList<>();
        int ticksSinceGround;
        double landingFall;
        long landingTick = Long.MIN_VALUE / 2;
        boolean sneakDown;
        long sneakPressTick = Long.MIN_VALUE / 2;
        Vec3 lastKick;
        Key liveMove;
        int liveSeq = -1;
        long liveSince;
        int bodySeq = -1;
        long bodyUntil;
        PendingFall pendingFall;
        MoveVerdict lastVerdict;
        long observedTick = Long.MIN_VALUE;

        void observe(ServerPlayer player, long tick) {
            boolean sneak = player.getLastClientInput().shift();
            if (sneak && !sneakDown) {
                sneakPressTick = tick;
            }
            sneakDown = sneak;
            if (observedTick == tick) {
                return;
            }
            observedTick = tick;
            if (player.onGround()) {
                ticksSinceGround = 0;
                lastKick = null;
            } else {
                ticksSinceGround++;
            }
        }
    }

    private final class Context implements ClaimContext {
        private final ServerPlayer player;
        private final Mover mover;
        private final int waited;

        Context(ServerPlayer player, Mover mover, int waited) {
            this.player = player;
            this.mover = mover;
            this.waited = waited;
        }

        @Override
        public BodyState body() {
            return MinecraftBodies.body(player);
        }

        @Override
        public MovementWorld world() {
            return MinecraftBodies.world(player.level());
        }

        @Override
        public long serverTick() {
            return tick;
        }

        @Override
        public int waitedTicks() {
            return waited;
        }

        @Override
        public int ticksSinceGround() {
            return mover.ticksSinceGround;
        }

        @Override
        public double lastLandingFall() {
            return mover.landingFall;
        }

        @Override
        public long ticksSinceLanding() {
            return tick - mover.landingTick;
        }

        @Override
        public long ticksSinceSneakPress() {
            return tick - mover.sneakPressTick;
        }

        @Override
        public Optional<Vec3> lastKickNormal() {
            return Optional.ofNullable(mover.lastKick);
        }

        @Override
        public double positionTolerance() {
            return moveset.core().positionTolerance();
        }
    }
}
