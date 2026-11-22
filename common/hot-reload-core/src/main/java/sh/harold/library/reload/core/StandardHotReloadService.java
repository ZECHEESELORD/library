package sh.harold.library.reload.core;

import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.DataDecoder;
import sh.harold.library.reload.HotReloadService;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.reload.ReloadError;
import sh.harold.library.reload.ReloadableData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The standard {@link HotReloadService}. {@link #poll()} reads each watched file and compares its bytes with the last
 * read, so a save is seen whatever the file system's timestamp resolution; data files are small. Parsing and decoding
 * happen in {@code poll()}; values become visible only in {@link #applyPending()}.
 *
 * <p>{@link #startPolling(Duration)} polls on a daemon thread; without it the owner calls {@code poll()} itself (a
 * gametest or a reload command does). Errors also go to the error sink given at construction, for a log line.
 */
public final class StandardHotReloadService implements HotReloadService {

    private final Consumer<? super ReloadError> errorSink;
    private final List<Watch<?>> watches = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService poller;
    private volatile boolean closed;

    public StandardHotReloadService() {
        this(error -> {
        });
    }

    public StandardHotReloadService(Consumer<? super ReloadError> errorSink) {
        this.errorSink = Objects.requireNonNull(errorSink, "errorSink");
    }

    @Override
    public <T> ReloadableData<T> watch(Path file, DataDecoder<T> decoder, Consumer<? super T> onApply) {
        Watch<T> watch = new Watch<>(file, decoder, onApply);
        Loaded<T> first = watch.load();
        if (first.error != null) {
            throw new DataDecodeException(first.error.path(), first.error.line(), first.error.describe());
        }
        return register(watch, first.value);
    }

    @Override
    public <T> ReloadableData<T> watch(Path file, DataDecoder<T> decoder, T fallback, Consumer<? super T> onApply) {
        Objects.requireNonNull(fallback, "fallback");
        Watch<T> watch = new Watch<>(file, decoder, onApply);
        Loaded<T> first = watch.load();
        if (first.error != null) {
            watch.error = first.error;
            errorSink.accept(first.error);
            return register(watch, fallback);
        }
        return register(watch, first.value);
    }

    private <T> ReloadableData<T> register(Watch<T> watch, T value) {
        requireOpen();
        watch.current = value;
        watch.version = 1;
        watches.add(watch);
        watch.onApply.accept(value);
        return watch;
    }

    @Override
    public int poll() {
        int staged = 0;
        for (Watch<?> watch : watches) {
            if (watch.poll()) {
                staged++;
            }
        }
        return staged;
    }

    @Override
    public int applyPending() {
        int applied = 0;
        for (Watch<?> watch : watches) {
            if (watch.apply()) {
                applied++;
            }
        }
        return applied;
    }

    /** Polls every {@code interval} on a daemon thread until the service closes. */
    public synchronized void startPolling(Duration interval) {
        requireOpen();
        if (poller != null) {
            throw new IllegalStateException("already polling");
        }
        long millis = Math.max(1L, interval.toMillis());
        poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "library-hot-reload");
            thread.setDaemon(true);
            return thread;
        });
        poller.scheduleWithFixedDelay(this::poll, millis, millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (poller != null) {
            poller.shutdownNow();
        }
        for (Watch<?> watch : watches) {
            watch.close();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("hot reload service is closed");
        }
    }

    private record Loaded<T>(T value, ReloadError error) {
    }

    private final class Watch<T> implements ReloadableData<T> {

        private final Path file;
        private final DataDecoder<T> decoder;
        private final Consumer<? super T> onApply;
        private byte[] lastBytes;
        private Loaded<T> pending;
        private volatile T current;
        private volatile long version;
        private volatile ReloadError error;
        private volatile boolean active = true;

        private Watch(Path file, DataDecoder<T> decoder, Consumer<? super T> onApply) {
            this.file = Objects.requireNonNull(file, "file");
            this.decoder = Objects.requireNonNull(decoder, "decoder");
            this.onApply = Objects.requireNonNull(onApply, "onApply");
        }

        /** Reads and decodes the file. Remembers the bytes so an unchanged file is not staged again. */
        private Loaded<T> load() {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (NoSuchFileException e) {
                lastBytes = null;
                return new Loaded<>(null, new ReloadError(file, "$", 0, "file not found"));
            } catch (IOException e) {
                return new Loaded<>(null, new ReloadError(file, "$", 0, "cannot read: " + e.getMessage()));
            }
            lastBytes = bytes;
            try {
                JsonValue root = JsonReader.parse(new String(bytes, StandardCharsets.UTF_8));
                T value = decoder.decode(root);
                if (value == null) {
                    return new Loaded<>(null, new ReloadError(file, "$", 0, "decoder returned null"));
                }
                return new Loaded<>(value, null);
            } catch (DataDecodeException e) {
                return new Loaded<>(null, ReloadError.of(file, e));
            } catch (RuntimeException e) {
                return new Loaded<>(null, new ReloadError(file, "$", 0, e.toString()));
            }
        }

        private synchronized boolean poll() {
            if (!active) {
                return false;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException e) {
                // A save in progress or a deleted file: keep the last good data and try again next poll.
                return false;
            }
            if (Arrays.equals(bytes, lastBytes)) {
                return false;
            }
            Loaded<T> loaded = load();
            pending = loaded;
            if (loaded.error != null) {
                errorSink.accept(loaded.error);
            }
            return true;
        }

        private boolean apply() {
            Loaded<T> staged;
            synchronized (this) {
                staged = pending;
                pending = null;
            }
            if (staged == null || !active) {
                return false;
            }
            if (staged.error != null) {
                error = staged.error;
                return false;
            }
            current = staged.value;
            version++;
            error = null;
            onApply.accept(staged.value);
            return true;
        }

        @Override
        public Path file() {
            return file;
        }

        @Override
        public T current() {
            return current;
        }

        @Override
        public long version() {
            return version;
        }

        @Override
        public Optional<ReloadError> lastError() {
            return Optional.ofNullable(error);
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            active = false;
            watches.remove(this);
        }
    }
}
