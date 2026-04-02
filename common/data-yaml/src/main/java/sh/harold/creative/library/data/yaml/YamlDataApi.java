package sh.harold.creative.library.data.yaml;

import sh.harold.creative.library.data.core.StoreBackedDataApi;

import java.nio.file.Path;
import java.util.concurrent.Executor;

public final class YamlDataApi extends StoreBackedDataApi {

    public YamlDataApi(Path rootDirectory) {
        super(new YamlDocumentStore(rootDirectory));
    }

    public YamlDataApi(Path rootDirectory, Executor executor) {
        super(new YamlDocumentStore(rootDirectory, executor));
    }
}
