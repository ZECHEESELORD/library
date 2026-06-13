package sh.harold.creative.library.impulse.paper;

import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
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

public final class PaperImpulsePlatform implements AutoCloseable {

    private final Map<UUID, StandardImpulseController> controllers = new LinkedHashMap<>();
    private final Function<UUID, Entity> entityLookup;
    private final BukkitTask tickTask;
    private boolean closed;

    public PaperImpulsePlatform(JavaPlugin plugin) {
        this(plugin, plugin.getServer()::getEntity);
    }

    public PaperImpulsePlatform(JavaPlugin plugin, Function<UUID, Entity> entityLookup) {
        JavaPlugin owningPlugin = Objects.requireNonNull(plugin, "plugin");
        this.entityLookup = Objects.requireNonNull(entityLookup, "entityLookup");
        this.tickTask = owningPlugin.getServer().getScheduler().runTaskTimer(owningPlugin, this::tick, 1L, 1L);
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
        tickTask.cancel();
        controllers.values().forEach(StandardImpulseController::close);
        controllers.clear();
    }

    private void tick() {
        controllers.entrySet().removeIf(entry -> {
            Entity entity = entityLookup.apply(entry.getKey());
            if (entity == null || !entity.isValid()) {
                entry.getValue().close();
                return true;
            }
            ComposedImpulse sample = entry.getValue().tick(actorState(entity));
            apply(entity, sample);
            return !entry.getValue().hasActiveImpulses();
        });
    }

    private static void apply(Entity entity, ComposedImpulse sample) {
        Vector velocity = sample.absoluteVelocity()
                .map(PaperImpulsePlatform::toVector)
                .orElseGet(() -> entity.getVelocity().clone().add(toVector(sample.additiveVelocity())));
        entity.setVelocity(velocity);
    }

    private static ImpulseActorState actorState(Entity entity) {
        Location location = entity.getLocation();
        Vector direction = location.getDirection();
        Vec3 forward = new Vec3(direction.getX(), direction.getY(), direction.getZ());
        return new ImpulseActorState(
                new Vec3(location.getX(), location.getY(), location.getZ()),
                Frame3.of(Vec3.ZERO, forward, Vec3.UNIT_Y)
        );
    }

    private static Vector toVector(Vec3 vector) {
        return new Vector(vector.x(), vector.y(), vector.z());
    }
}
