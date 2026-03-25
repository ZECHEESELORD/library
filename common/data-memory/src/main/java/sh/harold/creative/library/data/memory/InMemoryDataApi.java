package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.DataApi;
import sh.harold.creative.library.data.DocumentCollection;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryDataApi implements DataApi {

    private final InMemoryDocumentStore store;
    private final Map<String, InMemoryDocumentCollection> collections = new ConcurrentHashMap<>();

    public InMemoryDataApi() {
        this(new InMemoryDocumentStore());
    }

    public InMemoryDataApi(InMemoryDocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public DocumentCollection collection(String name) {
        return collections.computeIfAbsent(name, ignored -> new InMemoryDocumentCollection(name, store));
    }

    @Override
    public void close() {
        collections.clear();
        store.close();
    }
}
