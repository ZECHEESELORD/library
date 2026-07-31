package sh.harold.library.entity.paper;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Priority-aware handoff for work that must run on a Paper player's ownership lane. */
final class PaperViewerWorkQueue<T> {

    private final ConcurrentLinkedQueue<T> interactive = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<T> background = new ConcurrentLinkedQueue<>();

    void add(T work, ViewerWorkPriority priority) {
        Objects.requireNonNull(work, "work");
        queue(priority).add(work);
    }

    boolean remove(T work) {
        Objects.requireNonNull(work, "work");
        return interactive.remove(work) || background.remove(work);
    }

    int drainPrioritized(
            int interactiveLimit,
            int backgroundLimit,
            Consumer<? super T> action
    ) {
        Objects.requireNonNull(action, "action");
        int drained = drain(ViewerWorkPriority.INTERACTIVE, interactiveLimit, action);
        return drained + drain(ViewerWorkPriority.BACKGROUND, backgroundLimit, action);
    }

    int drainInteractive(int limit, Consumer<? super T> action) {
        return drain(ViewerWorkPriority.INTERACTIVE, limit, action);
    }

    private int drain(ViewerWorkPriority priority, int limit, Consumer<? super T> action) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        Objects.requireNonNull(action, "action");
        ConcurrentLinkedQueue<T> selected = queue(priority);
        int drained = 0;
        while (drained < limit) {
            T work = selected.poll();
            if (work == null) {
                break;
            }
            action.accept(work);
            drained++;
        }
        return drained;
    }

    void drainAll(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action");
        T work;
        while ((work = interactive.poll()) != null) {
            action.accept(work);
        }
        while ((work = background.poll()) != null) {
            action.accept(work);
        }
    }

    private ConcurrentLinkedQueue<T> queue(ViewerWorkPriority priority) {
        return switch (Objects.requireNonNull(priority, "priority")) {
            case INTERACTIVE -> interactive;
            case BACKGROUND -> background;
        };
    }
}

enum ViewerWorkPriority {
    INTERACTIVE,
    BACKGROUND
}
