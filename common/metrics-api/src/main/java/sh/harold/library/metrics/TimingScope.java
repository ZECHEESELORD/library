package sh.harold.library.metrics;

import java.time.Duration;

public interface TimingScope extends AutoCloseable {

    Duration elapsed();

    boolean stopped();

    void stop();

    @Override
    default void close() {
        stop();
    }
}
