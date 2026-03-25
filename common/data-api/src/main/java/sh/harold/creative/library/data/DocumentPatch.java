package sh.harold.creative.library.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DocumentPatch {

    private final Map<String, Object> setValues = new LinkedHashMap<>();
    private final List<String> removePaths = new ArrayList<>();

    public DocumentPatch set(String path, Object value) {
        Objects.requireNonNull(path, "path");
        setValues.put(path, value);
        removePaths.remove(path);
        return this;
    }

    public DocumentPatch remove(String path) {
        Objects.requireNonNull(path, "path");
        setValues.remove(path);
        removePaths.add(path);
        return this;
    }

    public Map<String, Object> setValues() {
        return Map.copyOf(setValues);
    }

    public List<String> removePaths() {
        return List.copyOf(removePaths);
    }

    public boolean isEmpty() {
        return setValues.isEmpty() && removePaths.isEmpty();
    }
}
