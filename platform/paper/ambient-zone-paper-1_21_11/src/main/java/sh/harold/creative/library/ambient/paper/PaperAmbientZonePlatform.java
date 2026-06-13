package sh.harold.creative.library.ambient.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.creative.library.ambient.ViewerAmbientState;
import sh.harold.creative.library.ambient.ZoneSpec;
import sh.harold.creative.library.ambient.core.StandardAmbientZoneController;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class PaperAmbientZonePlatform implements AutoCloseable {

    private final StandardAmbientZoneController controller;
    private final Supplier<List<ViewerAmbientState>> viewers;
    private final AnchorResolver anchorResolver;
    private final PaperAmbientSink sink;
    private final BukkitTask task;
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
        this.task = owningPlugin.getServer().getScheduler().runTaskTimer(owningPlugin, this::tick, 1L, 1L);
    }

    public KeyedHandle start(ZoneSpec spec) {
        return controller.start(spec);
    }

    public boolean stop(Key key) {
        return controller.stop(key);
    }

    public void stopAll() {
        controller.stopAll();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        task.cancel();
        controller.close();
    }

    private void tick() {
        if (closed) {
            return;
        }
        sink.accept(controller.tick(viewers.get(), anchorResolver));
    }
}
