package sh.harold.library.movement.fabric;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import sh.harold.library.movement.BodyOverride;
import sh.harold.library.movement.BodyState;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Live body overrides by entity, read by the hitbox and pose mixins on both sides. The server sets one when it
 * accepts (or provisionally receives) a roll or slide claim; the client sets one from its own engine's output. In
 * single player the two sides hold different entity objects, so one map serves both. Use from the owning side's
 * thread.
 */
public final class BodyOverrides {

    private static final Map<Entity, BodyOverride> OVERRIDES = new WeakHashMap<>();

    private BodyOverrides() {
    }

    public static synchronized BodyOverride get(Entity entity) {
        return OVERRIDES.isEmpty() ? null : OVERRIDES.get(entity);
    }

    /** Sets or clears ({@code null}) the override and refreshes the hitbox and pose at once. */
    public static void set(Entity entity, BodyOverride override) {
        Objects.requireNonNull(entity, "entity");
        BodyOverride previous;
        synchronized (BodyOverrides.class) {
            previous = override == null ? OVERRIDES.remove(entity) : OVERRIDES.put(entity, override);
        }
        if (Objects.equals(previous, override)) {
            return;
        }
        if (override != null) {
            entity.setPose(pose(override.pose()));
        }
        entity.refreshDimensions();
    }

    public static EntityDimensions dimensions(BodyOverride override) {
        return EntityDimensions.scalable((float) BodyState.PLAYER_WIDTH, (float) override.height())
                .withEyeHeight((float) override.eyeHeight());
    }

    public static Pose pose(BodyOverride.RenderPose pose) {
        return switch (pose) {
            case STANDING -> Pose.STANDING;
            case CROUCHING -> Pose.CROUCHING;
            case CRAWLING -> Pose.SWIMMING;
        };
    }
}
