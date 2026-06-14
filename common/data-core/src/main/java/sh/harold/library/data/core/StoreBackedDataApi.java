package sh.harold.library.data.core;

import sh.harold.library.data.DataApi;
import sh.harold.library.data.DataNamespace;
import sh.harold.library.data.DocumentStore;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class StoreBackedDataApi implements DataApi {

    private final DocumentStore store;
    private final Map<String, DataNamespace> namespaces = new ConcurrentHashMap<>();

    public StoreBackedDataApi(DocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    protected final DocumentStore store() {
        return store;
    }

    @Override
    public DataNamespace namespace(String name) {
        return namespaces.computeIfAbsent(name, ignored -> new StoreBackedDataNamespace(name, store));
    }

    @Override
    public void close() {
        namespaces.clear();
        store.close();
    }
}
