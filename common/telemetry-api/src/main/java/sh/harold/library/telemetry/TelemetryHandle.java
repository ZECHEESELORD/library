package sh.harold.library.telemetry;

/**
 * A running recording. Closing it stops recording, flushes and closes its sink. Idempotent.
 */
public interface TelemetryHandle extends AutoCloseable {

    boolean active();

    /** Rows written so far. */
    long rows();

    @Override
    void close();
}
