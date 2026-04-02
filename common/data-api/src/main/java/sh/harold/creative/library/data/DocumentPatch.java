package sh.harold.creative.library.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DocumentPatch {

    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final List<String> removePaths = new ArrayList<>();

    public DocumentPatch set(String path, Object value) {
        String normalizedPath = DocumentValues.requirePath(path);
        setValues.put(normalizedPath, DocumentValues.normalizeValue(value));
        removePaths.remove(normalizedPath);
        return this;
    }

    public DocumentPatch remove(String path) {
        String normalizedPath = DocumentValues.requirePath(path);
        setValues.remove(normalizedPath);
        if (!removePaths.contains(normalizedPath)) {
            removePaths.add(normalizedPath);
        }
        return this;
    }

    public Map<String, Object> setValues() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(setValues));
    }

    public List<String> removePaths() {
        return List.copyOf(removePaths);
    }

    public boolean isEmpty() {
        return setValues.isEmpty() && removePaths.isEmpty();
    }
}
