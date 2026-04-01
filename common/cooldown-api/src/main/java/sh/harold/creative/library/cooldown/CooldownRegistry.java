package sh.harold.creative.library.cooldown;

import java.time.Duration;
import java.util.Optional;

/**
 * Synchronous registry for reserving and querying cooldown windows.
 */
public interface CooldownRegistry extends AutoCloseable {

    CooldownAcquisition acquire(CooldownKey key, CooldownSpec spec);

    Optional<Duration> remaining(CooldownKey key);

    void clear(CooldownKey key);

    @Override
    void close();
}
