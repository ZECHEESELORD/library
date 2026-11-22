package sh.harold.library.movement.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MoveClaim;
import sh.harold.library.movement.MoveContext;
import sh.harold.library.movement.MoveDefinition;
import sh.harold.library.movement.MoveEnd;
import sh.harold.library.movement.MoveEvent;
import sh.harold.library.movement.MoveInstance;
import sh.harold.library.movement.MoveOutput;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.movement.core.moves.StockMoves;
import sh.harold.library.movement.core.tuning.CoreTuning;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs the enabled moves for one body, one tick at a time (architecture section 5). Pure apart from the
 * {@link MovementState} it is handed, so the client can run it predictively and replay it from a snapshot.
 *
 * <p>Order within a tick: input edges and the buffer, then ground and fall tracking (a touchdown is seen at the start of
 * the tick after the move that landed), then the live exclusive move (or a start, by priority), then the passive moves
 * if no exclusive move is live. With no move enabled the output is {@link MoveOutput#NONE}.
 */
public final class MovementEngine {

    public static final double VANILLA_GRAVITY = 0.08;

    private final CoreTuning core;
    private final List<MoveDefinition> exclusive;
    private final List<MoveDefinition> passive;
    private final Map<Key, MoveDefinition> byKey;

    public MovementEngine(CoreTuning core, List<MoveDefinition> definitions) {
        this.core = Objects.requireNonNull(core, "core");
        List<MoveDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparingInt(MoveDefinition::priority).reversed());
        this.exclusive = sorted.stream().filter(definition -> definition.kind() == MoveDefinition.Kind.EXCLUSIVE)
                .toList();
        this.passive = sorted.stream().filter(definition -> definition.kind() == MoveDefinition.Kind.PASSIVE)
                .toList();
        java.util.LinkedHashMap<Key, MoveDefinition> keys = new java.util.LinkedHashMap<>();
        for (MoveDefinition definition : sorted) {
            if (keys.put(definition.key(), definition) != null) {
                throw new IllegalArgumentException("two definitions for " + definition.key().asString());
            }
        }
        this.byKey = Map.copyOf(keys);
    }

    /** An engine for the stock moves a moveset enables. */
    public static MovementEngine of(Moveset moveset) {
        return new MovementEngine(moveset.core(), StockMoves.definitions(moveset));
    }

    public boolean empty() {
        return byKey.isEmpty();
    }

    public boolean has(Key move) {
        return byKey.containsKey(move);
    }

    public Optional<MoveDefinition> definition(Key move) {
        return Optional.ofNullable(byKey.get(move));
    }

    public CoreTuning core() {
        return core;
    }

    /** Steps {@code state} by one tick and returns what the host should do this tick. */
    public MoveOutput step(MovementState state, BodyState body, InputFrame input, MovementWorld world, long tick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(world, "world");
        if (!state.initialized) {
            state.initialized = true;
            state.previousY = body.position().y();
            state.previousOnGround = body.onGround();
            state.previousFall = body.fallDistance();
            state.previousInput = InputFrame.EMPTY;
            state.previousPosition = body.position();
        }
        state.lastMotion = body.position().subtract(state.previousPosition);
        Context context = new Context(state, body, input, world, tick);
        trackInput(state, input, tick);
        boolean landed = trackGround(state, body, tick);
        context.landed = landed;
        MoveOutput.Builder out = MoveOutput.builder();
        context.out = out;
        if (byKey.isEmpty()) {
            finish(state, body, input, tick);
            return MoveOutput.NONE;
        }
        if (body.exempt()) {
            if (state.active != null) {
                out.event(new MoveEvent.Ended(state.active.key(), tick, MoveEnd.EndReason.EXEMPT));
                state.active = null;
            }
            finish(state, body, input, tick);
            return out.build();
        }
        if (state.active != null) {
            MoveInstance interrupting = tryStarts(context, state.active);
            if (interrupting != null) {
                out.event(new MoveEvent.Ended(state.active.key(), tick, MoveEnd.EndReason.INTERRUPTED));
                begin(context, interrupting);
            } else {
                runActive(context);
            }
        } else {
            MoveInstance started = tryStarts(context, null);
            if (started != null) {
                begin(context, started);
            }
        }
        if (state.active == null) {
            runPassives(context);
        }
        out.status(status(state, tick));
        finish(state, body, input, tick);
        return out.build();
    }

    private void runActive(Context context) {
        MovementState state = context.state;
        MoveInstance instance = state.active;
        MoveInstance.Step step = instance.tick(context, context.out);
        switch (step) {
            case MoveInstance.Step.Continue ignored -> {
            }
            case MoveInstance.Step.End end -> {
                context.out.event(new MoveEvent.Ended(instance.key(), context.tick, end.reason()));
                state.active = null;
                context.endedThisTick = true;
            }
            case MoveInstance.Step.Handoff handoff -> {
                context.out.event(new MoveEvent.Ended(instance.key(), context.tick, MoveEnd.EndReason.CANCELLED));
                state.active = null;
                context.endedThisTick = true;
                MoveDefinition next = byKey.get(handoff.next());
                if (next != null && !context.vetoed(next.key())) {
                    context.handoffFrom = instance.key();
                    Optional<MoveInstance> started = next.tryStart(context);
                    context.handoffFrom = null;
                    started.ifPresent(value -> begin(context, value));
                }
            }
        }
    }

    private MoveInstance tryStarts(Context context, MoveInstance live) {
        for (MoveDefinition definition : exclusive) {
            if (live != null && !live.allowsStart(definition.key())) {
                continue;
            }
            if (context.vetoed(definition.key()) || context.coolingDown(definition.key())) {
                continue;
            }
            Optional<MoveInstance> started = definition.tryStart(context);
            if (started.isPresent()) {
                return started.get();
            }
        }
        return null;
    }

    private void begin(Context context, MoveInstance instance) {
        MovementState state = context.state;
        BodyState body = context.body;
        MoveClaim claim = instance.claim(MoveClaim.of(instance.key(), context.tick, body.position(), body.velocity()));
        context.out.event(new MoveEvent.Started(claim));
        state.active = instance;
        runActive(context);
    }

    private void runPassives(Context context) {
        MovementState state = context.state;
        for (MoveDefinition definition : passive) {
            MoveInstance instance = state.passives.get(definition.key());
            if (instance == null) {
                Optional<MoveInstance> created = definition.tryStart(context);
                if (created.isEmpty()) {
                    continue;
                }
                instance = created.get();
                state.passives.put(definition.key(), instance);
            }
            instance.tick(context, context.out);
            if (state.active != null) {
                break;
            }
        }
    }

    private MoveOutput.Status status(MovementState state, long tick) {
        if (state.active != null) {
            return new MoveOutput.Status(Optional.of(state.active.key()), tick - state.active.startTick(),
                    state.active.phase());
        }
        for (MoveInstance instance : state.passives.values()) {
            String phase = instance.phase();
            if (!"idle".equals(phase)) {
                return new MoveOutput.Status(Optional.of(instance.key()), tick - instance.startTick(), phase);
            }
        }
        return MoveOutput.Status.IDLE;
    }

    private void trackInput(MovementState state, InputFrame input, long tick) {
        InputFrame previous = state.previousInput;
        if (input.jump() && !previous.jump()) {
            state.lastJumpPress = tick;
            state.jumpConsumed = false;
        }
        if (input.sneak() && !previous.sneak()) {
            state.lastSneakPress = tick;
        }
        if (!input.sneak() && previous.sneak()) {
            state.lastSneakRelease = tick;
        }
    }

    private boolean trackGround(MovementState state, BodyState body, long tick) {
        boolean landed = body.onGround() && !state.previousOnGround;
        if (landed) {
            state.landingTick = tick;
            state.landingFall = state.previousFall + Math.max(0.0, state.previousY - body.position().y());
        }
        state.ticksSinceGround = body.onGround() ? 0 : state.ticksSinceGround + 1;
        if (body.onGround() && state.launchTick != tick) {
            state.launchBy = null;
        }
        return landed;
    }

    private void finish(MovementState state, BodyState body, InputFrame input, long tick) {
        state.previousInput = input;
        state.previousOnGround = body.onGround();
        state.previousY = body.position().y();
        state.previousPosition = body.position();
        state.previousFall = body.fallDistance();
        state.previousSprinting = body.sprinting();
        state.tick = tick;
        state.vetoUntil.values().removeIf(until -> until <= tick);
        state.cooldownUntil.values().removeIf(until -> until <= tick);
    }

    private final class Context implements MoveContext {

        private final MovementState state;
        private final BodyState body;
        private final InputFrame input;
        private final InputFrame previousInput;
        private final MovementWorld world;
        private final long tick;
        private boolean landed;
        private boolean endedThisTick;
        private Key handoffFrom;
        private MoveOutput.Builder out;

        private Context(MovementState state, BodyState body, InputFrame input, MovementWorld world, long tick) {
            this.state = state;
            this.body = body;
            this.input = input;
            this.previousInput = state.previousInput;
            this.world = world;
            this.tick = tick;
        }

        @Override
        public long tick() {
            return tick;
        }

        @Override
        public BodyState body() {
            return body;
        }

        @Override
        public InputFrame input() {
            return input;
        }

        @Override
        public InputFrame previousInput() {
            return previousInput;
        }

        @Override
        public MovementWorld world() {
            return world;
        }

        @Override
        public double gravity() {
            return VANILLA_GRAVITY;
        }

        @Override
        public Vec3 lastMotion() {
            return state.lastMotion;
        }

        @Override
        public <T> Optional<T> recall(Key key, Class<T> type) {
            Object value = state.memory.get(key);
            return type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        @Override
        public void remember(Key key, Object value) {
            state.memory.put(key, Objects.requireNonNull(value, "value"));
        }

        @Override
        public void forget(Key key) {
            state.memory.remove(key);
        }

        @Override
        public boolean jumpPressed() {
            return input.jump() && !previousInput.jump();
        }

        @Override
        public boolean sneakPressed() {
            return input.sneak() && !previousInput.sneak();
        }

        @Override
        public boolean sneakReleased() {
            return !input.sneak() && previousInput.sneak();
        }

        @Override
        public boolean jumpBuffered() {
            return !state.jumpConsumed && tick - state.lastJumpPress < core.inputBufferTicks();
        }

        @Override
        public boolean jumpPressedWithin(int ticks) {
            return tick - state.lastJumpPress <= ticks;
        }

        @Override
        public boolean sneakPressedWithin(int ticks) {
            return tick - state.lastSneakPress <= ticks;
        }

        @Override
        public void consumeJump() {
            state.jumpConsumed = true;
        }

        @Override
        public long lastJumpPress() {
            return state.lastJumpPress;
        }

        @Override
        public long lastSneakPress() {
            return state.lastSneakPress;
        }

        @Override
        public boolean chord() {
            boolean latestNow = state.lastJumpPress == tick || state.lastSneakPress == tick;
            return latestNow && Math.abs(state.lastJumpPress - state.lastSneakPress) <= core.chordTicks();
        }

        @Override
        public int ticksSinceGround() {
            return state.ticksSinceGround;
        }

        @Override
        public boolean coyote() {
            return !body.onGround() && state.ticksSinceGround <= core.coyoteTicks() && state.launchBy == null
                    && body.velocity().y() <= 0.0;
        }

        @Override
        public boolean landed() {
            return landed;
        }

        @Override
        public double landingFall() {
            return state.landingFall;
        }

        @Override
        public long landingTick() {
            return state.landingTick;
        }

        @Override
        public boolean sprintingRecently() {
            return body.sprinting() || state.previousSprinting;
        }

        @Override
        public void markLaunch(Key by, double verticalSpeed) {
            state.launchBy = by;
            state.launchTick = tick;
            state.launchPosition = body.position();
        }

        @Override
        public Optional<Key> lastLaunchBy() {
            return Optional.ofNullable(state.launchBy);
        }

        @Override
        public long lastLaunchTick() {
            return state.launchTick;
        }

        @Override
        public Vec3 lastLaunchPosition() {
            return state.launchPosition;
        }

        @Override
        public boolean coolingDown(Key move) {
            Long until = state.cooldownUntil.get(move);
            return until != null && tick < until;
        }

        @Override
        public void cooldown(Key move, int ticks) {
            state.cooldownUntil.put(move, tick + 1 + ticks);
        }

        @Override
        public boolean vetoed(Key move) {
            Long until = state.vetoUntil.get(move);
            return until != null && tick < until;
        }

        @Override
        public Optional<Key> handoffFrom() {
            return Optional.ofNullable(handoffFrom);
        }

        @Override
        public void claim(MoveClaim claim) {
            out.event(new MoveEvent.Started(claim));
        }

        @Override
        public void endClaim(Key move, MoveEnd.EndReason reason) {
            out.event(new MoveEvent.Ended(move, tick, reason));
        }
    }
}
