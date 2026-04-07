package sh.harold.creative.library.data;

import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DocumentStore extends AutoCloseable {

    CompletionStage<DocumentSnapshot> read(DocumentKey key);

    CompletionStage<WriteResult> write(DocumentKey key, Map<String, Object> data, WriteCondition condition);

    CompletionStage<WriteResult> patch(DocumentKey key, DocumentPatch patch, WriteCondition condition);

    CompletionStage<WriteResult> delete(DocumentKey key, WriteCondition condition);

    CompletionStage<Long> count(String namespace, String collection);

    CompletionStage<List<String>> listIds(String namespace, String collection);

    @Override
    default void close() {
    }
}
