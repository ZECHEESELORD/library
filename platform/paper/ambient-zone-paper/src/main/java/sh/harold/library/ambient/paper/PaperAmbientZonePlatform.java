package sh.harold.library.ambient.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import sh.harold.library.ambient.ViewerAmbientState;
import sh.harold.library.ambient.ZoneSpec;
import sh.harold.library.ambient.core.StandardAmbientZoneController;
import sh.harold.library.spatial.AnchorResolver;
import sh.harold.library.tick.KeyedHandle;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class PaperAmbientZonePlatform implements AutoCloseable {

    private final StandardAmbientZoneController controller;
    private final Supplier<List<ViewerAmbientState>> viewers;
    private final AnchorResolver anchorResolver;
    private final PaperAmbientSink sink;
    private final ScheduledTask task;
    private boolean closed;

    public PaperAmbientZonePlatform(
            JavaPlugin plugin,
            Supplier<List<ViewerAmbientState>> viewers,
            AnchorResolver anchorResolver,
            PaperAmbientSink sink
    ) {
        JavaPlugin owningPlugin = Objects.requireNonNull(plugin, "plugin");
        this.controller = new StandardAmbientZoneController();
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.task = owningPlugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                owningPlugin,
                ignored -> tick(),
                1L,
                1L
        );
    }

    public synchronized KeyedHandle start(ZoneSpec spec) {
        return controller.start(spec);
    }

    public synchronized boolean stop(Key key) {
        return controller.stop(key);
    }

    public synchronized void stopAll() {
        controller.stopAll();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        task.cancel();
        controller.close();
    }

    private synchronized void tick() {
        if (closed) {
            return;
        }
        sink.accept(controller.tick(viewers.get(), anchorResolver));
    }
}
