package sh.harold.library.sound.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import sh.harold.library.sound.SoundTarget;
import sh.harold.library.sound.core.ScheduledCueTask;
import sh.harold.library.sound.core.SoundCueScheduler;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class PaperSoundCueScheduler implements SoundCueScheduler {

    private final Plugin plugin;
    private final GlobalRegionScheduler globalScheduler;

    PaperSoundCueScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.globalScheduler = Objects.requireNonNull(
                plugin.getServer().getGlobalRegionScheduler(),
                "globalScheduler"
        );
    }

    PaperSoundCueScheduler(Plugin plugin, GlobalRegionScheduler globalScheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.globalScheduler = Objects.requireNonNull(globalScheduler, "globalScheduler");
    }

    @Override
    public ScheduledCueTask schedule(long delayTicks, Runnable action) {
        validate(delayTicks, action);
        ScheduledTask task = delayTicks == 0
                ? globalScheduler.run(plugin, ignored -> action.run())
                : globalScheduler.runDelayed(plugin, ignored -> action.run(), delayTicks);
        return task::cancel;
    }

    @Override
    public ScheduledCueTask schedule(SoundTarget target, long delayTicks, Runnable action) {
        return schedule(target, delayTicks, action, () -> {
        });
    }

    @Override
    public ScheduledCueTask schedule(
            SoundTarget target,
            long delayTicks,
            Runnable action,
            Runnable onDiscard
    ) {
        Objects.requireNonNull(target, "target");
        validate(delayTicks, action);
        Objects.requireNonNull(onDiscard, "onDiscard");
        if (target instanceof SoundTarget.AudienceTarget audienceTarget
                && audienceTarget.audience() instanceof Player player) {
            return schedule(player.getScheduler(), delayTicks, action, onDiscard);
        }
        return schedule(delayTicks, action);
    }

    private ScheduledCueTask schedule(
            EntityScheduler scheduler,
            long delayTicks,
            Runnable action,
            Runnable onDiscard
    ) {
        AtomicBoolean completed = new AtomicBoolean();
        Runnable runAction = () -> {
            if (completed.compareAndSet(false, true)) {
                action.run();
            }
        };
        Runnable discard = () -> {
            if (completed.compareAndSet(false, true)) {
                onDiscard.run();
            }
        };
        ScheduledTask task = delayTicks == 0
                ? scheduler.run(plugin, ignored -> runAction.run(), discard)
                : scheduler.runDelayed(plugin, ignored -> runAction.run(), discard, delayTicks);
        if (task == null) {
            discard.run();
            return () -> {
            };
        }
        return task::cancel;
    }

    private static void validate(long delayTicks, Runnable action) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        Objects.requireNonNull(action, "action");
    }
}
