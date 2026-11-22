package sh.harold.library.movement.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import sh.harold.library.movement.Aabb;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Converts between Minecraft's player and level and the movement core's body, input and world. */
public final class MinecraftBodies {

    private MinecraftBodies() {
    }

    /**
     * The player's body. It is exempt (moves stand down, vanilla runs) while spectating, flying, riding, gliding,
     * climbing or in a fluid.
     */
    public static BodyState body(Player player) {
        boolean exempt = player.isSpectator() || player.getAbilities().flying || player.isPassenger()
                || player.isFallFlying() || player.onClimbable() || player.isInWater() || player.isInLava();
        return new BodyState(vec(player.position()), vec(player.getDeltaMovement()), player.onGround(),
                player.horizontalCollision, player.fallDistance, player.getYRot(), player.getXRot(),
                player.isSprinting(), player.getBbWidth(), player.getBbHeight(), exempt);
    }

    public static InputFrame input(net.minecraft.world.entity.player.Input input) {
        return new InputFrame(input.forward(), input.backward(), input.left(), input.right(), input.jump(),
                input.shift(), input.sprint());
    }

    public static net.minecraft.world.entity.player.Input input(InputFrame frame) {
        return new net.minecraft.world.entity.player.Input(frame.forward(), frame.back(), frame.left(), frame.right(),
                frame.jump(), frame.sneak(), frame.sprint());
    }

    /** The level's block collision boxes, and the friction of the block under a body. */
    public static MovementWorld world(Level level) {
        return new MovementWorld() {
            @Override
            public List<Aabb> collisions(Aabb region) {
                List<Aabb> boxes = new ArrayList<>();
                AABB query = new AABB(region.minX(), region.minY(), region.minZ(), region.maxX(), region.maxY(),
                        region.maxZ());
                for (VoxelShape shape : level.getBlockCollisions(null, query)) {
                    for (AABB box : shape.toAabbs()) {
                        boxes.add(new Aabb(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
                    }
                }
                return boxes;
            }

            @Override
            public double friction(Aabb body) {
                BlockPos below = BlockPos.containing((body.minX() + body.maxX()) * 0.5, body.minY() - 0.5,
                        (body.minZ() + body.maxZ()) * 0.5);
                return level.getBlockState(below).getBlock().getFriction();
            }
        };
    }

    public static Vec3 vec(net.minecraft.world.phys.Vec3 v) {
        return new Vec3(v.x, v.y, v.z);
    }

    public static net.minecraft.world.phys.Vec3 vec(Vec3 v) {
        return new net.minecraft.world.phys.Vec3(v.x(), v.y(), v.z());
    }
}
