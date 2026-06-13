package sh.harold.creative.library.sound.paper;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.sound.core.ScheduledCueTask;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperSoundCueSchedulerTest {

    @Test
    void delegatesSchedulingAndCancellationToBukkit() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(4L))).thenReturn(task);

        PaperSoundCueScheduler soundScheduler = new PaperSoundCueScheduler(plugin);

        ScheduledCueTask scheduled = soundScheduler.schedule(4L, () -> {
        });
        scheduled.cancel();

        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), eq(4L));
        verify(task).cancel();
    }
}
