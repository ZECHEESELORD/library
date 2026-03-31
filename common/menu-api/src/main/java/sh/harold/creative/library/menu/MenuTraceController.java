package sh.harold.creative.library.menu;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class MenuTraceController {

    private final AtomicReference<MenuTraceSnapshot> snapshot = new AtomicReference<>(MenuTraceSnapshot.disabled());

    public MenuTraceSnapshot snapshot() {
        return snapshot.get();
    }

    public void disable() {
        snapshot.set(MenuTraceSnapshot.disabled());
    }

    public void traceAll() {
        snapshot.set(MenuTraceSnapshot.traceAll());
    }

    public void traceMenuTitles(Iterable<String> menuTitles) {
        snapshot.set(MenuTraceSnapshot.menuTitles(Objects.requireNonNull(menuTitles, "menuTitles")));
    }

    public boolean enabled() {
        return snapshot().enabled();
    }
}
