package sh.harold.library.telemetry;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A named column: how to read one value from a context of type {@code C} at a {@link FrameTime}.
 * Names are lower case ({@code [a-z][a-z0-9_.]*}), for example {@code pos_y} or {@code movement.move_tick};
 * {@code tick} and {@code partial} are reserved for the time columns every CSV starts with.
 */
public record TelemetryColumn<C>(String name, ColumnType type, Reader<C> reader) {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_.]*");

    public TelemetryColumn {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reader, "reader");
        requireValidName(name);
    }

    public static void requireValidName(String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("column name '" + name + "' must match [a-z][a-z0-9_.]*");
        }
        if (name.equals("tick") || name.equals("partial")) {
            throw new IllegalArgumentException("column name '" + name + "' is reserved");
        }
    }

    /** Reads a value; it must be a {@code Double}, {@code Long}, {@code Boolean} or {@code String} to match the type. */
    @FunctionalInterface
    public interface Reader<C> {

        Object read(C context, FrameTime time);
    }
}
