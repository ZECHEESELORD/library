package sh.harold.creative.library.sound;

@FunctionalInterface
public interface CuePlayback extends AutoCloseable {

    void cancel();

    @Override
    default void close() {
        cancel();
    }

    static CuePlayback noop() {
        return () -> {
        };
    }
}
