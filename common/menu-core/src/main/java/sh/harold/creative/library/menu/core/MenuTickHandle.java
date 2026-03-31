package sh.harold.creative.library.menu.core;

@FunctionalInterface
public interface MenuTickHandle extends AutoCloseable {

    void cancel();

    @Override
    default void close() {
        cancel();
    }

    static MenuTickHandle noop() {
        return () -> {
        };
    }
}
