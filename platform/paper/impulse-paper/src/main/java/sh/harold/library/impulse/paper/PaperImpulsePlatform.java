package sh.harold.library.impulse.paper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import sh.harold.library.impulse.ComposedImpulse;
import sh.harold.library.impulse.ImpulseActorState;
import sh.harold.library.impulse.ImpulseSpec;
import sh.harold.library.impulse.core.StandardImpulseController;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.Vec3;
import sh.harold.library.tick.KeyedHandle;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Applies each impulse on the affected entity's scheduler instead of a shared server thread. */
public final class PaperImpulsePlatform implements AutoCloseable {

    private final Plugin plugin;
    private final Map<UUID, EntitySession> sessions = new ConcurrentHashMap<>();
    private final Function<UUID, Entity> entityLookup;
    private volatile boolean closed;

    public PaperImpulsePlatform(JavaPlugin plugin) {
        this(plugin, plugin.getServer()::getEntity);
    }

    public PaperImpulsePlatform(JavaPlugin plugin, Function<UUID, Entity> entityLookup) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.entityLookup = Objects.requireNonNull(entityLookup, "entityLookup");
    }

    public KeyedHandle start(Entity entity, ImpulseSpec spec) {
        Objects.requireNonNull(entity, "entity");
        return start(entity.getUniqueId(), spec);
    }

    public KeyedHandle start(UUID entityId, ImpulseSpec spec) {
        ensureOpen();
        Entity entity = entityLookup.apply(Objects.requireNonNull(entityId, "entityId"));
        if (entity == null || !entity.isValid()) {
            throw new IllegalArgumentException("unknown or retired entity: " + entityId);
        }
        EntitySession session = sessions.compute(entityId, (ignored, existing) -> {
            if (existing == null || existing.closed()) {
                return new EntitySession(entity);
            }
            return existing;
        });
        return session.start(Objects.requireNonNull(spec, "spec"));
    }

    public boolean stop(UUID entityId, Key key) {
        EntitySession session = sessions.get(entityId);
        return session != null && session.stop(key);
    }

    public void stopAll(UUID entityId) {
        EntitySession session = sessions.remove(entityId);
        if (session != null) {
            session.close();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sessions.values().forEach(EntitySession::close);
        sessions.clear();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Paper impulse platform is closed");
        }
    }

    private final class EntitySession {
        private final UUID entityId;
        private final StandardImpulseController controller = new StandardImpulseController();
        private ScheduledTask tickTask;
        private boolean closed;

        private EntitySession(Entity entity) {
            this.entityId = entity.getUniqueId();
            this.tickTask = entity.getScheduler().runAtFixedRate(
                    plugin,
                    ignored -> tick(),
                    this::retire,
                    1L,
                    1L
            );
            if (tickTask == null) {
                closed = true;
            }
        }

        private synchronized KeyedHandle start(ImpulseSpec spec) {
            if (closed) {
                throw new IllegalStateException("entity retired before impulse could start");
            }
            return controller.start(spec);
        }

        private synchronized boolean stop(Key key) {
            if (closed) {
                return false;
            }
            boolean stopped = controller.stop(Objects.requireNonNull(key, "key"));
            closeIfIdle();
            return stopped;
        }

        private synchronized void tick() {
            if (closed) {
                return;
            }
            Entity entity = entityLookup.apply(entityId);
            if (entity == null || !entity.isValid()) {
                retire();
                return;
            }
            ComposedImpulse sample = controller.tick(actorState(entity));
            apply(entity, sample);
            closeIfIdle();
        }

        private synchronized void retire() {
            sessions.remove(entityId, this);
            close();
        }

        private synchronized void closeIfIdle() {
            if (!controller.hasActiveImpulses()) {
                sessions.remove(entityId, this);
                close();
            }
        }

        private synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
            controller.close();
        }

        private synchronized boolean closed() {
            return closed;
        }
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
