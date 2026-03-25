package sh.harold.creative.library.data;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;

public interface Document {

    DocumentKey key();

    boolean exists();

    <T> Optional<T> get(String path, Class<T> type);

    DocumentSnapshot snapshot();

    CompletionStage<DocumentSnapshot> snapshotAsync();

    CompletionStage<Void> set(String path, Object value);

    CompletionStage<Void> remove(String path);

    CompletionStage<Void> overwrite(Map<String, Object> data);

    CompletionStage<Void> update(UnaryOperator<Map<String, Object>> mutator);

    CompletionStage<Void> patch(DocumentPatch patch);
}
