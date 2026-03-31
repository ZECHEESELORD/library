package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.MenuTraceController;
import sh.harold.creative.library.menu.MenuTraceSnapshot;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MenuTrace {

    private static final ThreadLocal<MenuTraceSpan> CURRENT = new ThreadLocal<>();

    private MenuTrace() {
    }

    public static void withTrace(
            MenuTraceController controller,
            Consumer<String> sink,
            String host,
            UUID viewerId,
            String cause,
            Runnable action
    ) {
        Objects.requireNonNull(action, "action");
        MenuTraceSnapshot snapshot = Objects.requireNonNull(controller, "controller").snapshot();
        if (!snapshot.enabled()) {
            action.run();
            return;
        }
        withSpan(new MenuTraceSpan(snapshot, Objects.requireNonNull(sink, "sink"), host, viewerId, cause), action);
    }

    public static void withSpan(MenuTraceSpan span, Runnable action) {
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(action, "action");
        MenuTraceSpan previous = CURRENT.get();
        CURRENT.set(span);
        try {
            action.run();
        } finally {
            CURRENT.set(previous);
            span.finish();
        }
    }

    public static Runnable propagate(Runnable action) {
        Objects.requireNonNull(action, "action");
        MenuTraceSpan span = CURRENT.get();
        if (span == null) {
            return action;
        }
        span.retain();
        return () -> {
            MenuTraceSpan previous = CURRENT.get();
            CURRENT.set(span);
            try {
                action.run();
            } finally {
                CURRENT.set(previous);
                span.release();
            }
        };
    }

    public static void title(Component title) {
        if (title == null) {
            return;
        }
        title(ComponentText.flatten(title));
    }

    public static void title(String title) {
        MenuTraceSpan span = CURRENT.get();
        if (span != null) {
            span.markMenuTitle(title);
        }
    }

    public static void field(String key, Object value) {
        MenuTraceSpan span = CURRENT.get();
        if (span != null) {
            span.field(key, value);
        }
    }

    public static void setCount(String key, long value) {
        MenuTraceSpan span = CURRENT.get();
        if (span != null) {
            span.setCount(key, value);
        }
    }

    public static void incrementCount(String key) {
        incrementCount(key, 1L);
    }

    public static void incrementCount(String key, long delta) {
        MenuTraceSpan span = CURRENT.get();
        if (span != null) {
            span.incrementCount(key, delta);
        }
    }

    public static void addDuration(String stage, long nanos) {
        MenuTraceSpan span = CURRENT.get();
        if (span != null) {
            span.addDuration(stage, nanos);
        }
    }

    public static void detailIfSlow(String category, long nanos, Supplier<String> messageSupplier) {
        MenuTraceSpan span = CURRENT.get();
        if (span != null) {
            span.detailIfSlow(category, nanos, messageSupplier);
        }
    }

    public static void time(String stage, Runnable action) {
        Objects.requireNonNull(action, "action");
        MenuTraceSpan span = CURRENT.get();
        if (span == null) {
            action.run();
            return;
        }
        long started = System.nanoTime();
        try {
            action.run();
        } finally {
            span.addDuration(stage, System.nanoTime() - started);
        }
    }

    public static <T> T time(String stage, Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        MenuTraceSpan span = CURRENT.get();
        if (span == null) {
            return action.get();
        }
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            span.addDuration(stage, System.nanoTime() - started);
        }
    }
}
