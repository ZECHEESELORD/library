package sh.harold.creative.library.data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface DocumentCollection {

    String name();

    CompletionStage<Document> load(String id);

    CompletionStage<Document> put(String id, Map<String, Object> data);

    CompletionStage<Boolean> exists(String id);

    CompletionStage<Boolean> delete(String id);

    CompletionStage<List<Document>> all();

    CompletionStage<Long> count();
}
