package sh.harold.creative.library.data.memory;

import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

interface LocalDocumentStore extends DocumentStore {

    DocumentSnapshot readSnapshot(DocumentKey key);

    void writeNow(DocumentKey key, Map<String, Object> data);

    DocumentSnapshot updateSnapshot(DocumentKey key, UnaryOperator<Map<String, Object>> mutator);

    void patchNow(DocumentKey key, DocumentPatch patch);

    boolean deleteNow(DocumentKey key);

    List<DocumentSnapshot> allSnapshots(String collection);

    long countNow(String collection);
}
