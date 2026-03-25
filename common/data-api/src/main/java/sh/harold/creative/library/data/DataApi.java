package sh.harold.creative.library.data;

public interface DataApi extends AutoCloseable {

    DocumentCollection collection(String name);

    @Override
    default void close() {
    }
}
