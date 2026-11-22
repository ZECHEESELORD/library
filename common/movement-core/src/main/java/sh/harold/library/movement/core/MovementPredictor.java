package sh.harold.library.movement.core;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.prediction.Correction;
import sh.harold.library.prediction.ReplayResult;
import sh.harold.library.prediction.core.PredictionHistory;

import java.util.Objects;
import java.util.Optional;

/**
 * The client's record of what it predicted, for reconciling a rejected claim (architecture section 5). Each tick the
 * host records the input the engine read and the snapshot it observed at the end of the tick (the real game's body,
 * not a simulation). When the server rejects a claim, {@link #reject} rewinds to the tick before the move started,
 * forbids the move, and replays the stored inputs through {@link MovementSimulation}, so the host learns where the
 * body would be had the move never started. The host moves the body there and hands the gap to an error smoother:
 * the designed correction in place of a snap.
 */
public final class MovementPredictor {

    /** One recorded tick. {@code observed} is null on replayed ticks, which the simulation fills in. */
    public record Tick(InputFrame input, MovementSimulation.Snapshot observed) {

        public Tick {
            Objects.requireNonNull(input, "input");
        }
    }

    public static final int CAPACITY = 64;

    private final MovementSimulation simulation;
    private MovementWorld world = MovementWorld.EMPTY;
    private PredictionHistory<Tick, MovementSimulation.Snapshot> history;

    public MovementPredictor(MovementSimulation simulation) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
    }

    public MovementSimulation simulation() {
        return simulation;
    }

    /**
     * Records tick {@code tick}. A gap in the tick numbers (a pause, a respawn) starts a fresh history.
     * {@code observed} must not share mutable state with the live engine: pass a copy.
     */
    public void record(long tick, InputFrame input, MovementSimulation.Snapshot observed) {
        Objects.requireNonNull(observed, "observed");
        if (history == null || tick != history.currentTick() + 1) {
            history = new PredictionHistory<>(this::step, CAPACITY, (a, b) -> false, tick, observed);
            return;
        }
        history.advance(tick, new Tick(input, observed));
    }

    /** The newest recorded tick, or -1 before the first. */
    public long currentTick() {
        return history == null ? -1L : history.currentTick();
    }

    public Optional<MovementSimulation.Snapshot> stateAt(long tick) {
        return history == null ? Optional.empty() : history.stateAt(tick);
    }

    /**
     * Replays from the end of {@code startTick - 1} with {@code move} forbidden until {@code vetoUntil}. Returns
     * empty when that tick is no longer recorded; the host then only ends the move.
     */
    public Optional<ReplayResult<MovementSimulation.Snapshot>> reject(Key move, long startTick, long vetoUntil,
                                                                       MovementWorld world) {
        Objects.requireNonNull(move, "move");
        if (history == null) {
            return Optional.empty();
        }
        Optional<MovementSimulation.Snapshot> before = history.stateAt(startTick - 1);
        if (before.isEmpty()) {
            return Optional.empty();
        }
        MovementSimulation.Snapshot base = before.get().copy();
        base.movement().veto(move, vetoUntil);
        base.movement().dropActive(move);
        this.world = Objects.requireNonNull(world, "world");
        try {
            ReplayResult<MovementSimulation.Snapshot> result = history.reconcile(
                    new Correction<>(startTick - 1, base), (t, tick) -> new Tick(tick.input(), null));
            return result.outcome() == ReplayResult.Outcome.REPLAYED ? Optional.of(result) : Optional.empty();
        } finally {
            this.world = MovementWorld.EMPTY;
        }
    }

    public void clear() {
        history = null;
    }

    private MovementSimulation.Snapshot step(MovementSimulation.Snapshot state, Tick tick, long t) {
        return tick.observed() != null ? tick.observed() : simulation.step(state, tick.input(), world, t);
    }
}
