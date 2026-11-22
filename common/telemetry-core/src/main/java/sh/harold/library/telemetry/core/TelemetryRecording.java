package sh.harold.library.telemetry.core;

import sh.harold.library.telemetry.ColumnType;
import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetryColumn;
import sh.harold.library.telemetry.TelemetryRecorder;
import sh.harold.library.telemetry.TelemetryRegistry;
import sh.harold.library.telemetry.TelemetrySink;

import java.util.List;
import java.util.Objects;

/**
 * A recording over the columns a registry held when it started. Each {@link #record(Object, FrameTime)} writes
 * one row to the sink. A column whose reader throws or returns the wrong type writes an empty cell rather than
 * killing the recording. Thread-safe.
 */
public final class TelemetryRecording<C> implements TelemetryRecorder<C> {

    private final List<TelemetryColumn<C>> columns;
    private final List<String> names;
    private final TelemetrySink sink;
    private final Object monitor = new Object();
    private long rows;
    private boolean closed;

    private TelemetryRecording(List<TelemetryColumn<C>> columns, TelemetrySink sink) {
        this.columns = List.copyOf(columns);
        this.names = this.columns.stream().map(TelemetryColumn::name).toList();
        this.sink = sink;
        sink.header(names);
    }

    public static <C> TelemetryRecording<C> start(TelemetryRegistry<C> registry, TelemetrySink sink) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(sink, "sink");
        return new TelemetryRecording<>(registry.columns(), sink);
    }

    @Override
    public List<String> columnNames() {
        return names;
    }

    @Override
    public void record(C context, FrameTime time) {
        Objects.requireNonNull(time, "time");
        Object[] values = new Object[columns.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = read(columns.get(i), context, time);
        }
        synchronized (monitor) {
            if (closed) {
                return;
            }
            sink.row(time, values);
            rows++;
        }
    }

    @Override
    public boolean active() {
        synchronized (monitor) {
            return !closed;
        }
    }

    @Override
    public long rows() {
        synchronized (monitor) {
            return rows;
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            sink.flush();
            sink.close();
        }
    }

    private static <C> Object read(TelemetryColumn<C> column, C context, FrameTime time) {
        Object value;
        try {
            value = column.reader().read(context, time);
        } catch (RuntimeException exception) {
            return null;
        }
        return matches(column.type(), value) ? value : null;
    }

    private static boolean matches(ColumnType type, Object value) {
        return switch (type) {
            case DOUBLE -> value instanceof Double || value instanceof Float;
            case LONG -> value instanceof Long || value instanceof Integer;
            case BOOLEAN -> value instanceof Boolean;
            case TEXT -> value instanceof String;
        };
    }
}
