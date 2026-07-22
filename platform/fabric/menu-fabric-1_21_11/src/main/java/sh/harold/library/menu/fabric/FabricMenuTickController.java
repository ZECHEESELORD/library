package sh.harold.library.menu.fabric;

import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.menu.core.MenuTickScheduler;

import java.util.Objects;

final class FabricMenuTickController {

    private final MenuTickScheduler scheduler;
    private final Runnable tick;
    private MenuTickHandle handle = MenuTickHandle.noop();
    private long intervalTicks;

    FabricMenuTickController(MenuTickScheduler scheduler, Runnable tick) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.tick = Objects.requireNonNull(tick, "tick");
    }

    void update(long nextIntervalTicks) {
        if (nextIntervalTicks <= 0L) {
            stop();
            return;
        }
        if (intervalTicks == nextIntervalTicks) {
            return;
        }
        stop();
        handle = scheduler.schedule(nextIntervalTicks, tick);
        intervalTicks = nextIntervalTicks;
    }

    void stop() {
        handle.cancel();
        handle = MenuTickHandle.noop();
        intervalTicks = 0L;
    }
}
