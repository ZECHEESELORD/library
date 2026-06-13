package sh.harold.creative.library.sound.paper;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.creative.library.sound.core.ScheduledCueTask;
import sh.harold.creative.library.sound.core.SoundCueScheduler;

import java.util.Objects;

final class PaperSoundCueScheduler implements SoundCueScheduler {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    PaperSoundCueScheduler(Plugin plugin) {
        this(Objects.requireNonNull(plugin, "plugin"), plugin.getServer().getScheduler());
    }

    PaperSoundCueScheduler(Plugin plugin, BukkitScheduler scheduler) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public ScheduledCueTask schedule(long delayTicks, Runnable action) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("delayTicks cannot be negative");
        }
        BukkitTask task = scheduler.runTaskLater(plugin, Objects.requireNonNull(action, "action"), delayTicks);
        return task::cancel;
    }
}
