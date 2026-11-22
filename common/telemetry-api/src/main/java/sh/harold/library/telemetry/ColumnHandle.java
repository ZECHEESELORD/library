package sh.harold.library.telemetry;

/** A registered column. Closing it removes the column from recordings started afterwards. Idempotent. */
public interface ColumnHandle extends AutoCloseable {

    String name();

    boolean active();

    @Override
    void close();
}
