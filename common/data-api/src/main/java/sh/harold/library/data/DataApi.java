package sh.harold.library.data;

public interface DataApi extends AutoCloseable {

    DataNamespace namespace(String name);

    @Override
    default void close() {
    }
}
