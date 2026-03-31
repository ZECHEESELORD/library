package sh.harold.creative.library.telegraph.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.spatial.AnchorResolver;
import sh.harold.creative.library.telegraph.TelegraphSpec;
import sh.harold.creative.library.telegraph.core.StandardTelegraphController;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.Objects;

public final class MinestomTelegraphPlatform implements AutoCloseable {

    private final StandardTelegraphController controller;
    private final AnchorResolver anchorResolver;
    private final MinestomTelegraphSink sink;
    private final Task task;
    private boolean closed;

    public MinestomTelegraphPlatform(Scheduler scheduler, AnchorResolver anchorResolver, MinestomTelegraphSink sink) {
        Scheduler runtimeScheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.controller = new StandardTelegraphController();
        this.anchorResolver = Objects.requireNonNull(anchorResolver, "anchorResolver");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.task = runtimeScheduler.scheduleTask(this::tick, TaskSchedule.tick(1), TaskSchedule.tick(1));
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
        task.cancel();
        controller.close();
    }

    private void tick() {
        if (closed) {
            return;
        }
        sink.accept(controller.tick(anchorResolver));
    }
}
