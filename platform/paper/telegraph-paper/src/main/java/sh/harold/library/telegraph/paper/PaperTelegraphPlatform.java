package sh.harold.library.telegraph.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import sh.harold.library.spatial.AnchorResolver;
import sh.harold.library.telegraph.TelegraphSpec;
import sh.harold.library.telegraph.core.StandardTelegraphController;
import sh.harold.library.tick.KeyedHandle;

import java.util.Objects;

public final class PaperTelegraphPlatform implements AutoCloseable {

    private final StandardTelegraphController controller;
    private final AnchorResolver anchorResolver;
    private final PaperTelegraphSink sink;
    private final ScheduledTask tickTask;
    private boolean closed;

    public PaperTelegraphPlatform(JavaPlugin plugin, AnchorResolver anchorResolver, PaperTelegraphSink sink) {
        JavaPlugin owningPlugin = Objects.requireNonNull(plugin, "plugin");
        this.controller = new StandardTelegraphController();
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.tickTask = owningPlugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                owningPlugin,
                ignored -> tick(),
                1L,
                1L
        );
    }

    public synchronized KeyedHandle start(TelegraphSpec spec) {
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
        tickTask.cancel();
        controller.close();
    }

    private synchronized void tick() {
        if (closed) {
            return;
        }
        sink.accept(controller.tick(anchorResolver));
    }
}
