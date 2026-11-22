package sh.harold.library.prediction.core;

import sh.harold.library.prediction.Correction;
import sh.harold.library.prediction.PredictionModel;
import sh.harold.library.prediction.ReplayResult;

import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Client-side prediction with rewind and replay (architecture section 5, "Lagless"). Each tick the caller
 * {@link #advance(long, Object) advances} with that tick's input; the history keeps the input and the resulting
 * state for the last {@code capacity} ticks (64 by default). When a verdict corrects tick {@code t}, the
 * history restores the corrected state at {@code t}, re-simulates {@code t + 1} up to the current tick with the
 * stored inputs through the same {@link PredictionModel}, and rewrites the stored states.
 *
 * <p>Not thread-safe; drive it from the owning tick thread.
 */
public final class PredictionHistory<I, S> {

    private final PredictionModel<I, S> model;
    private final BiPredicate<S, S> sameState;
    private final HistoryRing<I> inputs;
    private final HistoryRing<S> states;
    private long currentTick;
    private S current;

    /**
     * @param sameState tolerance check used to recognise a correction that only confirms the prediction
     */
    public PredictionHistory(PredictionModel<I, S> model, int capacity, BiPredicate<S, S> sameState,
                             long startTick, S startState) {
        this.model = Objects.requireNonNull(model, "model");
        this.sameState = Objects.requireNonNull(sameState, "sameState");
        this.inputs = new HistoryRing<>(capacity);
        this.states = new HistoryRing<>(capacity);
        reset(startTick, startState);
    }

    public PredictionHistory(PredictionModel<I, S> model, long startTick, S startState) {
        this(model, HistoryRing.DEFAULT_CAPACITY, Object::equals, startTick, startState);
    }

    /** Discards history and starts again from {@code state} at the end of {@code tick}. */
    public void reset(long tick, S state) {
        Objects.requireNonNull(state, "state");
        inputs.clear();
        states.clear();
        states.put(tick, state);
        currentTick = tick;
        current = state;
    }

    public long currentTick() {
        return currentTick;
    }

    public S current() {
        return current;
    }

    public java.util.Optional<S> stateAt(long tick) {
        return states.get(tick);
    }

    public java.util.Optional<I> inputAt(long tick) {
        return inputs.get(tick);
    }

    /** Simulates {@code tick} (which must be {@code currentTick() + 1}) with {@code input} and stores both. */
    public S advance(long tick, I input) {
        Objects.requireNonNull(input, "input");
        if (tick != currentTick + 1) {
            throw new IllegalArgumentException("expected tick " + (currentTick + 1) + " but got " + tick);
        }
        S next = Objects.requireNonNull(model.step(current, input, tick), "model returned null");
        inputs.put(tick, input);
        states.put(tick, next);
        currentTick = tick;
        current = next;
        return next;
    }

    public ReplayResult<S> reconcile(Correction<S> correction) {
        return reconcile(correction, (tick, input) -> input);
    }

    /**
     * Applies {@code correction}. {@code rewriter} may change the stored inputs replayed after the corrected
     * tick, so a rejected move can be replayed as "the move never started"; rewritten inputs are stored.
     */
    public ReplayResult<S> reconcile(Correction<S> correction, InputRewriter<I> rewriter) {
        Objects.requireNonNull(correction, "correction");
        Objects.requireNonNull(rewriter, "rewriter");
        long tick = correction.tick();
        S before = current;
        if (tick > currentTick) {
            return new ReplayResult<>(ReplayResult.Outcome.FUTURE, tick, 0L, before, before);
        }
        if (!states.contains(tick)) {
            return new ReplayResult<>(ReplayResult.Outcome.TOO_OLD, tick, 0L, before, before);
        }
        S predicted = states.get(tick).orElseThrow();
        if (sameState.test(predicted, correction.state())) {
            return new ReplayResult<>(ReplayResult.Outcome.CONFIRMED, tick, 0L, before, before);
        }
        S state = correction.state();
        states.set(tick, state);
        long replayed = 0L;
        for (long t = tick + 1; t <= currentTick; t++) {
            I input = rewriter.rewrite(t, inputs.get(t).orElseThrow());
            state = Objects.requireNonNull(model.step(state, input, t), "model returned null");
            inputs.set(t, input);
            states.set(t, state);
            replayed++;
        }
        current = state;
        return new ReplayResult<>(ReplayResult.Outcome.REPLAYED, tick, replayed, before, state);
    }

    /** Rewrites a stored input during replay. */
    @FunctionalInterface
    public interface InputRewriter<I> {

        I rewrite(long tick, I input);
    }
}
