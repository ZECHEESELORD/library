package sh.harold.library.telemetry.core;

import sh.harold.library.telemetry.ColumnHandle;
import sh.harold.library.telemetry.TelemetryColumn;
import sh.harold.library.telemetry.TelemetryRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A thread-safe {@link TelemetryRegistry} keeping columns in registration order. */
public final class StandardTelemetryRegistry<C> implements TelemetryRegistry<C> {

    private final Map<String, TelemetryColumn<C>> columns = new LinkedHashMap<>();

    @Override
    public ColumnHandle register(TelemetryColumn<C> column) {
        Objects.requireNonNull(column, "column");
        synchronized (columns) {
            if (columns.containsKey(column.name())) {
                throw new IllegalStateException("telemetry column '" + column.name() + "' is already registered");
            }
            columns.put(column.name(), column);
        }
        return new Handle(column);
    }

    @Override
    public List<TelemetryColumn<C>> columns() {
        synchronized (columns) {
            return List.copyOf(new ArrayList<>(columns.values()));
        }
    }

    private final class Handle implements ColumnHandle {

        private final TelemetryColumn<C> column;

        private Handle(TelemetryColumn<C> column) {
            this.column = column;
        }

        @Override
        public String name() {
            return column.name();
        }

        @Override
        public boolean active() {
            synchronized (columns) {
                return columns.get(column.name()) == column;
            }
        }

        @Override
        public void close() {
            synchronized (columns) {
                columns.remove(column.name(), column);
            }
        }
    }
}
