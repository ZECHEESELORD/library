package sh.harold.creative.library.data.yaml;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import sh.harold.creative.library.data.DocumentKey;
import sh.harold.creative.library.data.DocumentPatch;
import sh.harold.creative.library.data.DocumentSnapshot;
import sh.harold.creative.library.data.DocumentStore;
import sh.harold.creative.library.data.DocumentValues;
import sh.harold.creative.library.data.WriteCondition;
import sh.harold.creative.library.data.WriteResult;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class YamlDocumentStore implements DocumentStore {

    private final Path rootDirectory;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final Map<DocumentKey, Object> locks = new ConcurrentHashMap<>();
    private final Map<DocumentKey, Long> revisions = new ConcurrentHashMap<>();

    public YamlDocumentStore(Path rootDirectory) {
        this(rootDirectory, createExecutor(), true);
    }

    public YamlDocumentStore(Path rootDirectory, Executor executor) {
        this(rootDirectory, executor, false);
    }

    private YamlDocumentStore(Path rootDirectory, Executor executor, boolean closeExecutor) {
        this.rootDirectory = validateRoot(rootDirectory);
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ownedExecutor = closeExecutor && executor instanceof ExecutorService service ? service : null;
    }

    @Override
    public CompletionStage<DocumentSnapshot> read(DocumentKey key) {
        return CompletableFuture.supplyAsync(() -> readSync(key), executor);
    }

    @Override
    public CompletionStage<WriteResult> write(DocumentKey key, Map<String, Object> data, WriteCondition condition) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock(key)) {
                DocumentSnapshot current = readSync(key);
                if (!condition.matches(current)) {
                    return WriteResult.conditionFailed(current);
                }
                long nextRevision = nextRevision(key, current.revision());
                Map<String, Object> normalized = DocumentValues.normalizeRoot(data);
                writeFile(key, nextRevision, normalized);
                return WriteResult.applied(new DocumentSnapshot(key, normalized, true, Long.toString(nextRevision)));
            }
        }, executor);
    }

    @Override
    public CompletionStage<WriteResult> patch(DocumentKey key, DocumentPatch patch, WriteCondition condition) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock(key)) {
                DocumentSnapshot current = readSync(key);
                if (!condition.matches(current)) {
                    return WriteResult.conditionFailed(current);
                }
                Map<String, Object> working = current.exists()
                        ? DocumentValues.deepCopyMap(current.data())
                        : new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : patch.setValues().entrySet()) {
                    DocumentValues.writePath(working, entry.getKey(), entry.getValue());
                }
                for (String path : patch.removePaths()) {
                    DocumentValues.removePath(working, path);
                }
                if (!current.exists() && working.isEmpty()) {
                    return WriteResult.applied(current);
                }
                long nextRevision = nextRevision(key, current.revision());
                writeFile(key, nextRevision, working);
                return WriteResult.applied(new DocumentSnapshot(key, working, true, Long.toString(nextRevision)));
            }
        }, executor);
    }

    @Override
    public CompletionStage<WriteResult> delete(DocumentKey key, WriteCondition condition) {
        return CompletableFuture.supplyAsync(() -> {
            synchronized (lock(key)) {
                DocumentSnapshot current = readSync(key);
                if (!condition.matches(current)) {
                    return WriteResult.conditionFailed(current);
                }
                if (!current.exists()) {
                    return WriteResult.applied(current);
                }
                long nextRevision = nextRevision(key, current.revision());
                try {
                    Files.deleteIfExists(pathFor(key));
                } catch (IOException exception) {
                    throw new IllegalStateException("failed to delete YAML document " + key, exception);
                }
                revisions.put(key, nextRevision);
                return WriteResult.applied(new DocumentSnapshot(key, Map.of(), false, Long.toString(nextRevision)));
            }
        }, executor);
    }

    @Override
    public CompletionStage<Long> count(String namespace, String collection) {
        return CompletableFuture.supplyAsync(() -> {
            Path directory = collectionDirectory(namespace, collection);
            if (!Files.isDirectory(directory)) {
                return 0L;
            }
            try (var stream = Files.list(directory)) {
                return stream.filter(path -> path.getFileName().toString().endsWith(".yml")).count();
            } catch (IOException exception) {
                throw new IllegalStateException("failed to count YAML documents in " + directory, exception);
            }
        }, executor);
    }

    @Override
    public CompletionStage<List<String>> listIds(String namespace, String collection) {
        return CompletableFuture.supplyAsync(() -> {
            Path directory = collectionDirectory(namespace, collection);
            if (!Files.isDirectory(directory)) {
                return List.of();
            }
            try (var stream = Files.list(directory)) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".yml"))
                        .map(path -> path.getFileName().toString())
                        .map(fileName -> fileName.substring(0, fileName.length() - 4))
                        .map(YamlDocumentStore::decodeSegment)
                        .sorted()
                        .toList();
            } catch (IOException exception) {
                throw new IllegalStateException("failed to list YAML documents in " + directory, exception);
            }
        }, executor);
    }

    @Override
    public void close() {
        if (ownedExecutor != null) {
            ownedExecutor.shutdownNow();
        }
    }

    private DocumentSnapshot readSync(DocumentKey key) {
        Objects.requireNonNull(key, "key");
        Path path = pathFor(key);
        if (!Files.isRegularFile(path)) {
            return new DocumentSnapshot(key, Map.of(), false, Long.toString(revisions.getOrDefault(key, 0L)));
        }
        Map<String, Object> payload = loadYaml(path);
        long revision = ((Number) payload.getOrDefault("revision", 0L)).longValue();
        revisions.put(key, revision);
        Object data = payload.get("data");
        if (!(data instanceof Map<?, ?> map)) {
            throw new IllegalStateException("YAML document is missing data map: " + path);
        }
        return new DocumentSnapshot(key, DocumentValues.deepCopyMap((Map<String, Object>) map), true, Long.toString(revision));
    }

    private void writeFile(DocumentKey key, long revision, Map<String, Object> data) {
        Path path = pathFor(key);
        try {
            Files.createDirectories(path.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("revision", revision);
            payload.put("data", DocumentValues.normalizeRoot(data));
            Path temporary = Files.createTempFile(path.getParent(), "document-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                yaml().dump(payload, writer);
            }
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            revisions.put(key, revision);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write YAML document " + key, exception);
        }
    }

    private Map<String, Object> loadYaml(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Object loaded = yaml().load(reader);
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalStateException("YAML document root must be a map: " + path);
            }
            return DocumentValues.deepCopyMap((Map<String, Object>) map);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read YAML document " + path, exception);
        }
    }

    private long nextRevision(DocumentKey key, String currentRevision) {
        long current = parseRevision(currentRevision);
        long known = revisions.getOrDefault(key, 0L);
        long next = Math.max(current, known) + 1L;
        revisions.put(key, next);
        return next;
    }

    private long parseRevision(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private Path collectionDirectory(String namespace, String collection) {
        return rootDirectory.resolve(encodeSegment(namespace)).resolve(encodeSegment(collection));
    }

    private Path pathFor(DocumentKey key) {
        return collectionDirectory(key.namespace(), key.collection()).resolve(encodeSegment(key.id()) + ".yml");
    }

    private Object lock(DocumentKey key) {
        return locks.computeIfAbsent(key, ignored -> new Object());
    }

    private static Path validateRoot(Path rootDirectory) {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException exception) {
            throw new IllegalArgumentException("failed to initialize YAML root directory " + rootDirectory, exception);
        }
        if (!Files.isDirectory(rootDirectory)) {
            throw new IllegalArgumentException("YAML root is not a directory: " + rootDirectory);
        }
        if (!Files.isWritable(rootDirectory)) {
            throw new IllegalArgumentException("YAML root is not writable: " + rootDirectory);
        }
        return rootDirectory.toAbsolutePath().normalize();
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "data-yaml");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return new Yaml(options);
    }

    private static String encodeSegment(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_' || character == '.') {
                builder.append(character);
                continue;
            }
            builder.append('%');
            builder.append(String.format("%04x", (int) character));
        }
        return builder.toString();
    }

    private static String decodeSegment(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); ) {
            char character = value.charAt(index);
            if (character != '%') {
                builder.append(character);
                index++;
                continue;
            }
            if (index + 5 > value.length()) {
                throw new IllegalArgumentException("invalid encoded segment: " + value);
            }
            String hex = value.substring(index + 1, index + 5);
            builder.append((char) Integer.parseInt(hex, 16));
            index += 5;
        }
        return builder.toString();
    }
}
