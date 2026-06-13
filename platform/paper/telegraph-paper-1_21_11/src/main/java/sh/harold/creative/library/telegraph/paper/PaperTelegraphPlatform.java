package sh.harold.creative.library.telegraph.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.telegraph.TelegraphSpec;
import sh.harold.creative.library.telegraph.core.StandardTelegraphController;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.Objects;

public final class PaperTelegraphPlatform implements AutoCloseable {

    private final StandardTelegraphController controller;
    private final AnchorResolver anchorResolver;
    private final PaperTelegraphSink sink;
    private final BukkitTask tickTask;
    private boolean closed;

    public PaperTelegraphPlatform(JavaPlugin plugin, AnchorResolver anchorResolver, PaperTelegraphSink sink) {
        JavaPlugin owningPlugin = Objects.requireNonNull(plugin, "plugin");
        this.controller = new StandardTelegraphController();
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.tickTask = owningPlugin.getServer().getScheduler().runTaskTimer(owningPlugin, this::tick, 1L, 1L);
    }

    public KeyedHandle start(TelegraphSpec spec) {
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
        tickTask.cancel();
        controller.close();
    }

    private void tick() {
        if (closed) {
            return;
        }
        sink.accept(controller.tick(anchorResolver));
    }
}
