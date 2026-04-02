package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.core.StoreBackedDataApi;

public final class InMemoryDataApi extends StoreBackedDataApi {

    public InMemoryDataApi() {
        this(new InMemoryDocumentStore());
    }

    public InMemoryDataApi(DocumentStore store) {
        super(store);
    }
}
