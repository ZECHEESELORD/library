package sh.harold.creative.library.ambient.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.ambient.ViewerAmbientState;
import sh.harold.creative.library.ambient.ZoneSpec;
import sh.harold.creative.library.ambient.core.StandardAmbientZoneController;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class MinestomAmbientZonePlatform implements AutoCloseable {

    private final StandardAmbientZoneController controller;
    private final Supplier<List<ViewerAmbientState>> viewers;
    private final AnchorResolver anchorResolver;
    private final MinestomAmbientSink sink;
    private final Task task;
    private boolean closed;

    public MinestomAmbientZonePlatform(
            Scheduler scheduler,
            Supplier<List<ViewerAmbientState>> viewers,
            AnchorResolver anchorResolver,
            MinestomAmbientSink sink
    ) {
        Scheduler runtimeScheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.controller = new StandardAmbientZoneController();
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.task = runtimeScheduler.scheduleTask(this::tick, TaskSchedule.tick(1), TaskSchedule.tick(1));
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
