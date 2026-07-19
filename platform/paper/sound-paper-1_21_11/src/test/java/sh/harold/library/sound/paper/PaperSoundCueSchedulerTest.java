package sh.harold.library.sound.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import sh.harold.library.sound.SoundTarget;
import sh.harold.library.sound.core.ScheduledCueTask;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperSoundCueSchedulerTest {

    @Test
    void schedulesPlayerAudienceOnItsEntityScheduler() {
        Plugin plugin = mock(Plugin.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(Runnable.class), eq(4L))).thenReturn(task);

        PaperSoundCueScheduler soundScheduler = new PaperSoundCueScheduler(plugin, globalScheduler);

        ScheduledCueTask scheduled = soundScheduler.schedule(SoundTarget.audience(player), 4L, () -> {
        });
        scheduled.cancel();

        verify(entityScheduler).runDelayed(eq(plugin), any(), any(Runnable.class), eq(4L));
        verify(task).cancel();
    }

    @Test
    void schedulesNonPlayerTargetsOnTheGlobalRegion() {
        Plugin plugin = mock(Plugin.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(globalScheduler.runDelayed(eq(plugin), any(), eq(4L))).thenReturn(task);

        PaperSoundCueScheduler soundScheduler = new PaperSoundCueScheduler(plugin, globalScheduler);

        ScheduledCueTask scheduled = soundScheduler.schedule(SoundTarget.emitter(sound -> {
        }), 4L, () -> {
        });
        scheduled.cancel();

        verify(globalScheduler).runDelayed(eq(plugin), any(), eq(4L));
        verify(task).cancel();
    }

    @Test
    void alreadyRetiredPlayerDiscardsWithoutRunningTheAction() {
        Plugin plugin = mock(Plugin.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        AtomicInteger actions = new AtomicInteger();
        AtomicInteger discards = new AtomicInteger();
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), eq(4L))).thenReturn(null);

        PaperSoundCueScheduler soundScheduler = new PaperSoundCueScheduler(plugin, globalScheduler);
        soundScheduler.schedule(
                SoundTarget.audience(player),
                4L,
                actions::incrementAndGet,
                discards::incrementAndGet
        );

        assertEquals(0, actions.get());
        assertEquals(1, discards.get());
    }

    @Test
    void laterRetiredPlayerDiscardsOnceAndCannotRunTheActionAfterward() {
        Plugin plugin = mock(Plugin.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        AtomicReference<Consumer<ScheduledTask>> scheduledAction = new AtomicReference<>();
        AtomicReference<Runnable> retired = new AtomicReference<>();
        AtomicInteger actions = new AtomicInteger();
        AtomicInteger discards = new AtomicInteger();
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), eq(4L))).thenAnswer(invocation -> {
            scheduledAction.set(invocation.getArgument(1));
            retired.set(invocation.getArgument(2));
            return task;
        });

        PaperSoundCueScheduler soundScheduler = new PaperSoundCueScheduler(plugin, globalScheduler);
        soundScheduler.schedule(
                SoundTarget.audience(player),
                4L,
                actions::incrementAndGet,
                discards::incrementAndGet
        );
        retired.get().run();
        scheduledAction.get().accept(task);
        retired.get().run();

        assertEquals(0, actions.get());
        assertEquals(1, discards.get());
    }

    @Test
    void resolvesTheGlobalRegionSchedulerFromTheServer() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getGlobalRegionScheduler()).thenReturn(globalScheduler);

        new PaperSoundCueScheduler(plugin);

        verify(server).getGlobalRegionScheduler();
    }
}
