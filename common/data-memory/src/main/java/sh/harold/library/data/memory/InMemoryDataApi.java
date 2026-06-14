package sh.harold.library.data.memory;

import sh.harold.library.data.DocumentStore;
import sh.harold.library.data.core.StoreBackedDataApi;

public final class InMemoryDataApi extends StoreBackedDataApi {

    public InMemoryDataApi() {
        this(new InMemoryDocumentStore());
    }

    public InMemoryDataApi(DocumentStore store) {
        super(store);
    }
}
