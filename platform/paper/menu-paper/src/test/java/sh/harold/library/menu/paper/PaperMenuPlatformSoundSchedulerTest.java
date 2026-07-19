package sh.harold.library.menu.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import sh.harold.library.sound.SoundCueService;
import sh.harold.library.sound.SoundTarget;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static sh.harold.library.sound.SoundCues.atTick;
import static sh.harold.library.sound.SoundCues.sequence;
import static sh.harold.library.sound.SoundCues.sound;

class PaperMenuPlatformSoundSchedulerTest {

    @Test
    void alreadyRetiredPlayerDoesNotPlayDelayedMenuSound() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getGlobalRegionScheduler()).thenReturn(globalScheduler);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), eq(4L))).thenReturn(null);

        SoundCueService sounds = defaultSounds(plugin);
        sounds.play(SoundTarget.audience(player), sequence(
                atTick(4, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));
        sounds.close();

        verify(player, never()).playSound(any(Sound.class));
    }

    @Test
    void laterRetiredPlayerDiscardsDelayedMenuSoundWithoutLeakingPlayback() throws Exception {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        GlobalRegionScheduler globalScheduler = mock(GlobalRegionScheduler.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        AtomicReference<Consumer<ScheduledTask>> scheduledAction = new AtomicReference<>();
        AtomicReference<Runnable> retired = new AtomicReference<>();
        when(plugin.getServer()).thenReturn(server);
        when(server.getGlobalRegionScheduler()).thenReturn(globalScheduler);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), eq(4L))).thenAnswer(invocation -> {
            scheduledAction.set(invocation.getArgument(1));
            retired.set(invocation.getArgument(2));
            return task;
        });

        SoundCueService sounds = defaultSounds(plugin);
        sounds.play(SoundTarget.audience(player), sequence(
                atTick(4, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));
        retired.get().run();
        scheduledAction.get().accept(task);
        sounds.close();

        verify(player, never()).playSound(any(Sound.class));
        verify(task, never()).cancel();
    }

    private static SoundCueService defaultSounds(JavaPlugin plugin) throws Exception {
        Method factory = PaperMenuPlatform.class.getDeclaredMethod("defaultSounds", JavaPlugin.class);
        factory.setAccessible(true);
        return (SoundCueService) factory.invoke(null, plugin);
    }
}
