package sh.harold.library.reload;

import java.nio.file.Path;
import java.util.Optional;

/**
 * One watched data file. {@link #current()} changes only when the service applies pending reloads, which the owner does
 * on a tick boundary, so a tick never sees half-old, half-new data.
 */
public interface ReloadableData<T> extends AutoCloseable {

    Path file();

    /** The last good value. */
    T current();

    /** How many values have been applied, the first load included. */
    long version();

    /** The error from the latest load attempt, cleared by the next good load. */
    Optional<ReloadError> lastError();

    boolean active();

    /** Stops watching. Idempotent; {@link #current()} keeps returning the last good value. */
    @Override
    void close();
}
