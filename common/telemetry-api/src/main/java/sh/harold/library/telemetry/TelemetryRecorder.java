package sh.harold.library.telemetry;

/**
 * A recording driven by its owner: each {@link #record(Object, FrameTime)} call reads every column of the
 * schema snapshot taken at start and writes one row. Host adapters call it from their tick or frame hooks;
 * headless tools call it from a loop.
 */
public interface TelemetryRecorder<C> extends TelemetryHandle {

    /** The column names in this recording, in CSV order after {@code tick,partial}. */
    java.util.List<String> columnNames();

    /** Writes one row read from {@code context} at {@code time}. Ignored after close. */
    void record(C context, FrameTime time);
}
