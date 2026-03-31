package sh.harold.creative.library.menu;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record MenuTraceSnapshot(
        boolean enabled,
        boolean allMenus,
        Set<String> menuTitles,
        long slowDetailThresholdNanos
) {

    public static final long DEFAULT_SLOW_DETAIL_THRESHOLD_NANOS = 1_000_000L;

    public MenuTraceSnapshot {
        menuTitles = Set.copyOf(new LinkedHashSet<>(menuTitles));
        if (slowDetailThresholdNanos < 0L) {
            throw new IllegalArgumentException("slowDetailThresholdNanos cannot be negative");
        }
    }

    public static MenuTraceSnapshot disabled() {
        return new MenuTraceSnapshot(false, false, Set.of(), DEFAULT_SLOW_DETAIL_THRESHOLD_NANOS);
    }

    public static MenuTraceSnapshot traceAll() {
        return new MenuTraceSnapshot(true, true, Set.of(), DEFAULT_SLOW_DETAIL_THRESHOLD_NANOS);
    }

    public static MenuTraceSnapshot menuTitles(Iterable<String> menuTitles) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        for (String menuTitle : menuTitles) {
            String value = Objects.requireNonNull(menuTitle, "menuTitle").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("menuTitle cannot be blank");
            }
            titles.add(value);
        }
        if (titles.isEmpty()) {
            throw new IllegalArgumentException("menuTitles cannot be empty");
        }
        return new MenuTraceSnapshot(true, false, titles, DEFAULT_SLOW_DETAIL_THRESHOLD_NANOS);
    }

    public boolean matchesMenuTitle(String menuTitle) {
        if (!enabled) {
            return false;
        }
        if (allMenus) {
            return true;
        }
        if (menuTitle == null) {
            return false;
        }
        for (String candidate : menuTitles) {
            if (candidate.equalsIgnoreCase(menuTitle)) {
                return true;
            }
        }
        return false;
    }
}
