package sh.harold.library.telemetry;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.function.Function;

/**
 * The field registry: features register named columns read from a context of type {@code C} (a server
 * player, a client frame, a headless fixture). A recording snapshots the columns when it starts, so
 * registering or closing a column later changes only recordings started afterwards. Implementations are
 * thread-safe.
 */
public interface TelemetryRegistry<C> {

    /** Registers a column. Fails if the name is already registered. */
    ColumnHandle register(TelemetryColumn<C> column);

    default ColumnHandle registerDouble(String name, ToDoubleFunction<C> source) {
        return register(new TelemetryColumn<>(name, ColumnType.DOUBLE, (context, time) -> source.applyAsDouble(context)));
    }

    /** A double column that also sees the row's time, for per-frame values such as a spring sampled at the partial. */
    default ColumnHandle registerTimedDouble(String name, ToDoubleBiFunction<C, FrameTime> source) {
        return register(new TelemetryColumn<>(name, ColumnType.DOUBLE, source::applyAsDouble));
    }

    default ColumnHandle registerLong(String name, ToLongFunction<C> source) {
        return register(new TelemetryColumn<>(name, ColumnType.LONG, (context, time) -> source.applyAsLong(context)));
    }

    default ColumnHandle registerBoolean(String name, Predicate<C> source) {
        return register(new TelemetryColumn<>(name, ColumnType.BOOLEAN, (context, time) -> source.test(context)));
    }

    default ColumnHandle registerText(String name, Function<C, String> source) {
        return register(new TelemetryColumn<>(name, ColumnType.TEXT, (context, time) -> source.apply(context)));
    }

    /** The registered columns in registration order. */
    List<TelemetryColumn<C>> columns();
}
