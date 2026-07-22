package sh.harold.library.menu.fabric;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class FabricMenuCallbackGate {

    private final Consumer<Runnable> nextTickScheduler;
    private final ArrayDeque<Runnable> deferredLifecycle = new ArrayDeque<>();
    private int depth;
    private long generation;
    private boolean retired;

    FabricMenuCallbackGate(Consumer<Runnable> nextTickScheduler) {
        this.nextTickScheduler = Objects.requireNonNull(nextTickScheduler, "nextTickScheduler");
    }

    synchronized long generation() {
        return generation;
    }

    synchronized boolean unchanged(long expectedGeneration) {
        return generation == expectedGeneration;
    }

    synchronized boolean defer(Runnable lifecycle) {
        Objects.requireNonNull(lifecycle, "lifecycle");
        if (retired || depth == 0) {
            return false;
        }
        generation++;
        deferredLifecycle.addLast(lifecycle);
        return true;
    }

    <T> T invoke(Supplier<T> callback) {
        Objects.requireNonNull(callback, "callback");
        begin();
        boolean completed = false;
        try {
            T result = callback.get();
            completed = true;
            return result;
        } finally {
            scheduleDeferredAtOutermostExit(completed);
        }
    }

    void invoke(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        invoke(() -> {
            callback.run();
            return null;
        });
    }

    synchronized void retire() {
        retired = true;
        generation++;
        deferredLifecycle.clear();
    }

    private synchronized void begin() {
        if (retired) {
            throw new IllegalStateException("Menu session callback gate is retired");
        }
        depth++;
    }

    private void scheduleDeferredAtOutermostExit(boolean completed) {
        List<Runnable> pending = List.of();
        synchronized (this) {
            depth--;
            if (depth < 0) {
                depth = 0;
                throw new IllegalStateException("Menu session callback depth underflow");
            }
            if (depth == 0 && !retired && !deferredLifecycle.isEmpty()) {
                if (completed) {
                    pending = new ArrayList<>(deferredLifecycle);
                }
                deferredLifecycle.clear();
            }
        }
        pending.forEach(nextTickScheduler);
    }
}
