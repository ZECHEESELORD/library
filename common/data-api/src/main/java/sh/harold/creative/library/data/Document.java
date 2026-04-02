package sh.harold.creative.library.data;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface Document {

    DocumentKey key();

    CompletionStage<DocumentSnapshot> read();

    default CompletionStage<WriteResult> write(Map<String, Object> data) {
        return write(data, WriteCondition.none());
    }

    CompletionStage<WriteResult> write(Map<String, Object> data, WriteCondition condition);

    default CompletionStage<WriteResult> patch(DocumentPatch patch) {
        return patch(patch, WriteCondition.none());
    }

    CompletionStage<WriteResult> patch(DocumentPatch patch, WriteCondition condition);

    default CompletionStage<WriteResult> delete() {
        return delete(WriteCondition.none());
    }

    CompletionStage<WriteResult> delete(WriteCondition condition);
}
