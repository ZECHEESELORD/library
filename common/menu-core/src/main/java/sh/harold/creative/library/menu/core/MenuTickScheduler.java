package sh.harold.creative.library.menu.core;

import java.util.Objects;

@FunctionalInterface
public interface MenuTickScheduler {

    MenuTickHandle schedule(long intervalTicks, Runnable action);

    static MenuTickScheduler unsupported() {
        return (intervalTicks, action) -> {
            if (intervalTicks <= 0L) {
                throw new IllegalArgumentException("intervalTicks must be greater than zero");
            }
            Objects.requireNonNull(action, "action");
            throw new UnsupportedOperationException("Reactive menu ticking is not enabled for this runtime");
        };
    }
}
