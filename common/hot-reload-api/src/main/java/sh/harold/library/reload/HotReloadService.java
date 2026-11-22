package sh.harold.library.reload;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Watches JSON data files (tuning, cues, tracks) and swaps new values in at a tick boundary (architecture 7.5, "Hot
 * reload"). Loading happens in {@link #poll()}, on any thread; nothing becomes visible until the owner calls
 * {@link #applyPending()} from its tick. A file that fails to parse or decode keeps its last good value and reports a
 * {@link ReloadError} naming the file, key and field. Closing the service closes every watch.
 */
public interface HotReloadService extends AutoCloseable {

    /**
     * Loads {@code file} now and watches it. {@code onApply} runs on the applying thread for the first value and every
     * reload. Fails with {@link DataDecodeException} if the first load fails, since there is no good value to keep.
     */
    <T> ReloadableData<T> watch(Path file, DataDecoder<T> decoder, Consumer<? super T> onApply);

    /** As {@link #watch(Path, DataDecoder, Consumer)}, starting from {@code fallback} if the first load fails. */
    <T> ReloadableData<T> watch(Path file, DataDecoder<T> decoder, T fallback, Consumer<? super T> onApply);

    /** Checks every watched file now and stages changed ones. Returns how many were staged (good or bad). */
    int poll();

    /** Swaps staged values in and runs their listeners. Call on a tick boundary. Returns how many values changed. */
    int applyPending();

    @Override
    void close();
}
