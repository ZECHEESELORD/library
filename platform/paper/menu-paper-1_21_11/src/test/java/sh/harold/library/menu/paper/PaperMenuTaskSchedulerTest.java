package sh.harold.library.menu.paper;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.core.MenuTickHandle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaperMenuTaskSchedulerTest {

    @Test
    void schedulesTicksOnTheViewerEntityScheduler() {
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runAtFixedRate(eq(plugin), any(), any(), eq(3L), eq(3L))).thenReturn(task);

        MenuTickHandle handle = PaperMenuTaskScheduler.folia(plugin).schedule(player, 3L, () -> { });
        handle.cancel();

        verify(entityScheduler).runAtFixedRate(eq(plugin), any(), any(), eq(3L), eq(3L));
        verify(task).cancel();
    }

    @Test
    void treatsRetiredViewerSchedulingAsANoop() {
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), eq(1L))).thenReturn(null);

        MenuTickHandle handle = PaperMenuTaskScheduler.folia(plugin).next(player, () -> { });
        handle.cancel();

        verify(entityScheduler).runDelayed(eq(plugin), any(), any(), eq(1L));
    }

    @Test
    void schedulesOneShotDelaysOnTheViewerEntityScheduler() {
        Plugin plugin = mock(Plugin.class);
        Player player = mock(Player.class);
        EntityScheduler entityScheduler = mock(EntityScheduler.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(player.getScheduler()).thenReturn(entityScheduler);
        when(entityScheduler.runDelayed(eq(plugin), any(), any(), eq(77L))).thenReturn(task);

        MenuTickHandle handle = PaperMenuTaskScheduler.folia(plugin).after(player, 77L, () -> { });
        handle.cancel();

        verify(entityScheduler).runDelayed(eq(plugin), any(), any(), eq(77L));
        verify(task).cancel();
    }

    @Test
    void schedulesWorldReadsOnTheLocationRegionScheduler() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        RegionScheduler regionScheduler = mock(RegionScheduler.class);
        Location location = mock(Location.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getRegionScheduler()).thenReturn(regionScheduler);
        when(regionScheduler.run(eq(plugin), eq(location), any())).thenReturn(task);

        MenuTickHandle handle = PaperMenuTaskScheduler.folia(plugin).at(location, () -> { });
        handle.cancel();

        verify(regionScheduler).run(eq(plugin), eq(location), any());
        verify(task).cancel();
    }
}
