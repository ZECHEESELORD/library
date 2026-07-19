package sh.harold.library.menu.paper;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.library.menu.core.MenuTickHandle;
import sh.harold.library.menu.core.MenuTickScheduler;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

interface PaperMenuTaskScheduler {

    MenuTickHandle schedule(Player player, long intervalTicks, Runnable action);

    MenuTickHandle next(Player player, Runnable action);

    MenuTickHandle after(Player player, long delayTicks, Runnable action);

    MenuTickHandle at(Location location, Runnable action);

    static PaperMenuTaskScheduler testing(
            MenuTickScheduler tickScheduler,
            Function<Runnable, MenuTickHandle> nextTickScheduler
    ) {
        return testing(tickScheduler, nextTickScheduler, (location, action) -> {
            action.run();
            return MenuTickHandle.noop();
        });
    }

    static PaperMenuTaskScheduler testing(
            MenuTickScheduler tickScheduler,
            Function<Runnable, MenuTickHandle> nextTickScheduler,
            BiFunction<Location, Runnable, MenuTickHandle> locationScheduler
    ) {
        return testing(tickScheduler, nextTickScheduler, locationScheduler,
                (player, delayTicks, action) -> MenuTickHandle.noop());
    }

    static PaperMenuTaskScheduler testing(
            MenuTickScheduler tickScheduler,
            Function<Runnable, MenuTickHandle> nextTickScheduler,
            BiFunction<Location, Runnable, MenuTickHandle> locationScheduler,
            DelayedTaskScheduler delayedTaskScheduler
    ) {
        Objects.requireNonNull(tickScheduler, "tickScheduler");
        Objects.requireNonNull(nextTickScheduler, "nextTickScheduler");
        Objects.requireNonNull(locationScheduler, "locationScheduler");
        Objects.requireNonNull(delayedTaskScheduler, "delayedTaskScheduler");
        return new PaperMenuTaskScheduler() {
            @Override
            public MenuTickHandle schedule(Player player, long intervalTicks, Runnable action) {
                return tickScheduler.schedule(intervalTicks, action);
            }

            @Override
            public MenuTickHandle next(Player player, Runnable action) {
                return nextTickScheduler.apply(action);
            }

            @Override
            public MenuTickHandle after(Player player, long delayTicks, Runnable action) {
                return delayedTaskScheduler.schedule(player, delayTicks, action);
            }

            @Override
            public MenuTickHandle at(Location location, Runnable action) {
                return locationScheduler.apply(location, action);
            }
        };
    }

    static PaperMenuTaskScheduler folia(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new PaperMenuTaskScheduler() {
            @Override
            public MenuTickHandle schedule(Player player, long intervalTicks, Runnable action) {
                if (intervalTicks <= 0L) {
                    throw new IllegalArgumentException("intervalTicks must be greater than zero");
                }
                var task = Objects.requireNonNull(player, "player").getScheduler().runAtFixedRate(
                        plugin,
                        ignored -> action.run(),
                        () -> { },
                        intervalTicks,
                        intervalTicks);
                return task == null ? MenuTickHandle.noop() : task::cancel;
            }

            @Override
            public MenuTickHandle next(Player player, Runnable action) {
                var task = Objects.requireNonNull(player, "player").getScheduler().runDelayed(
                        plugin,
                        ignored -> action.run(),
                        () -> { },
                        1L);
                return task == null ? MenuTickHandle.noop() : task::cancel;
            }

            @Override
            public MenuTickHandle after(Player player, long delayTicks, Runnable action) {
                if (delayTicks <= 0L) {
                    throw new IllegalArgumentException("delayTicks must be greater than zero");
                }
                var task = Objects.requireNonNull(player, "player").getScheduler().runDelayed(
                        plugin,
                        ignored -> Objects.requireNonNull(action, "action").run(),
                        () -> { },
                        delayTicks);
                return task == null ? MenuTickHandle.noop() : task::cancel;
            }

            @Override
            public MenuTickHandle at(Location location, Runnable action) {
                var task = plugin.getServer().getRegionScheduler().run(
                        plugin,
                        Objects.requireNonNull(location, "location"),
                        ignored -> Objects.requireNonNull(action, "action").run());
                return task == null ? MenuTickHandle.noop() : task::cancel;
            }
        };
    }

    @FunctionalInterface
    interface DelayedTaskScheduler {

        MenuTickHandle schedule(Player player, long delayTicks, Runnable action);
    }
}
