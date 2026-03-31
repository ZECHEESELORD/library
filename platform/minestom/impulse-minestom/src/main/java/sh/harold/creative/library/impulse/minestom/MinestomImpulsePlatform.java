package sh.harold.creative.library.impulse.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.impulse.ComposedImpulse;
import sh.harold.creative.library.impulse.ImpulseActorState;
import sh.harold.creative.library.impulse.ImpulseSpec;
import sh.harold.creative.library.impulse.core.StandardImpulseController;
import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Vec3;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class MinestomImpulsePlatform implements AutoCloseable {

    private final Map<UUID, StandardImpulseController> controllers = new LinkedHashMap<>();
    private final Function<UUID, Entity> entityLookup;
    private final Task task;
    private boolean closed;

    public MinestomImpulsePlatform(Scheduler scheduler, Function<UUID, Entity> entityLookup) {
        Scheduler runtimeScheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.entityLookup = Objects.requireNonNull(entityLookup, "entityLookup");
        this.task = runtimeScheduler.scheduleTask(this::tick, TaskSchedule.tick(1), TaskSchedule.tick(1));
    }

    public KeyedHandle start(UUID entityId, ImpulseSpec spec) {
        return controllers.computeIfAbsent(entityId, ignored -> new StandardImpulseController()).start(spec);
    }

    public boolean stop(UUID entityId, Key key) {
        StandardImpulseController controller = controllers.get(entityId);
        return controller != null && controller.stop(key);
    }

    public void stopAll(UUID entityId) {
        StandardImpulseController controller = controllers.get(entityId);
        if (controller != null) {
            controller.stopAll();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        task.cancel();
        controllers.values().forEach(StandardImpulseController::close);
        controllers.clear();
    }

    private void tick() {
        controllers.entrySet().removeIf(entry -> {
            Entity entity = entityLookup.apply(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                entry.getValue().close();
                return true;
            }
            ComposedImpulse sample = entry.getValue().tick(actorState(entity));
            apply(entity, sample);
            return !entry.getValue().hasActiveImpulses();
        });
    }

    private static void apply(Entity entity, ComposedImpulse sample) {
        Vec velocity = sample.absoluteVelocity()
                .map(MinestomImpulsePlatform::toVec)
                .orElseGet(() -> entity.getVelocity().add(toVec(sample.additiveVelocity())));
        entity.setVelocity(velocity);
    }

    private static ImpulseActorState actorState(Entity entity) {
        Pos position = entity.getPosition();
        Vec direction = position.direction();
        Vec3 forward = new Vec3(direction.x(), direction.y(), direction.z());
        return new ImpulseActorState(
                new Vec3(position.x(), position.y(), position.z()),
                Frame3.of(Vec3.ZERO, forward, Vec3.UNIT_Y)
        );
    }

    private static Vec toVec(Vec3 vector) {
        return new Vec(vector.x(), vector.y(), vector.z());
    }
}
