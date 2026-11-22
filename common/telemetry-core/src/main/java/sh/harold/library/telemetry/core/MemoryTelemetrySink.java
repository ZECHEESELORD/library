package sh.harold.library.telemetry.core;

import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetrySink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Keeps rows in memory, optionally as a ring of the last {@code capacity} rows (a "last N seconds" buffer),
 * and can write them out as the same CSV the file sink writes. Thread-safe.
 */
public final class MemoryTelemetrySink implements TelemetrySink {

    private final int capacity;
    private final Deque<Row> rows = new ArrayDeque<>();
    private List<String> columns = List.of();

    /** Unbounded. */
    public MemoryTelemetrySink() {
        this(Integer.MAX_VALUE);
    }

    public MemoryTelemetrySink(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void header(List<String> columns) {
        this.columns = List.copyOf(columns);
    }

    @Override
    public synchronized void row(FrameTime time, Object[] values) {
        if (rows.size() == capacity) {
            rows.removeFirst();
        }
        rows.addLast(new Row(time, values.clone()));
    }

    public synchronized List<String> columns() {
        return columns;
    }

    public synchronized List<Row> rows() {
        return List.copyOf(new ArrayList<>(rows));
    }

    /** Writes the header and the retained rows as CSV. */
    public synchronized void writeCsv(Writer writer) {
        CsvTelemetrySink csv = new CsvTelemetrySink(writer);
        csv.header(columns);
        for (Row row : rows) {
            csv.row(row.time(), row.values());
        }
        try {
            writer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    /** One retained row. */
    public record Row(FrameTime time, Object[] values) {

        public Object value(int column) {
            return values[column];
        }
    }
}
