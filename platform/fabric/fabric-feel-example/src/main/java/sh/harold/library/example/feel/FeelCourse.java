package sh.harold.library.example.feel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Builds the movement test course on a stone floor at a known origin. Every station has a named start: stand there,
 * face {@link Station#yaw()} and run the scripted input. Coordinates are relative to the origin, which is the floor's
 * top surface (the player's feet) at the course's south-west corner; the course fills x 0..75, z 0..20, up to 16
 * blocks high, and is cleared to air first.
 *
 * <ul>
 *   <li>{@code ledge_2}, {@code ledge_3}, {@code ledge_4}: walls of that height 3 blocks ahead (+z), 3 deep.</li>
 *   <li>{@code ledge_2_no_headroom}: a 2-high ledge with a block one above its top, so nobody fits on it.</li>
 *   <li>{@code drop_4}, {@code drop_8}, {@code drop_13}: pillar tops that height above the floor; walk forward off
 *       the edge (+z) and roll.</li>
 *   <li>{@code duct}: 8 blocks of run-up, then a 1-block-high duct 6 blocks long (+z).</li>
 *   <li>{@code shaft_2}, {@code shaft_3}, {@code shaft_4}: two 12-high walls that far apart (along x), the start
 *       against the west wall facing east (+x).</li>
 *   <li>{@code single_wall}: one 12-high wall, the start against its east face, facing it (-x).</li>
 * </ul>
 */
public final class FeelCourse {

    /** A named start point: block position of the feet and the yaw to face (0 is +z, -90 is +x, 90 is -x). */
    public record Station(String name, BlockPos start, float yaw) {
    }

    /** The built course. */
    public record Course(BlockPos origin, Map<String, Station> stations) {

        public Station station(String name) {
            Station station = stations.get(name);
            if (station == null) {
                throw new NoSuchElementException("no station '" + name + "'; stations: " + stations.keySet());
            }
            return station;
        }
    }

    public static final List<String> STATIONS = List.of("ledge_2", "ledge_3", "ledge_4", "ledge_2_no_headroom",
            "drop_4", "drop_8", "drop_13", "duct", "shaft_2", "shaft_3", "shaft_4", "single_wall");

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private FeelCourse() {
    }

    public static Course build(ServerLevel level, BlockPos origin) {
        Builder b = new Builder(level, origin);
        b.fill(-2, 0, -2, 77, 16, 22, AIR);
        b.fill(-2, -1, -2, 77, -1, 22, STONE);
        Map<String, Station> stations = new LinkedHashMap<>();

        int[] ledges = {2, 3, 4};
        for (int i = 0; i < ledges.length; i++) {
            int x = i * 6;
            b.fill(x - 1, 0, 3, x + 1, ledges[i] - 1, 5, STONE);
            stations.put("ledge_" + ledges[i], b.station("ledge_" + ledges[i], x, 0, 1, 0.0f));
        }
        b.fill(17, 0, 3, 19, 1, 5, STONE);
        b.fill(17, 3, 3, 19, 3, 5, STONE);
        stations.put("ledge_2_no_headroom", b.station("ledge_2_no_headroom", 18, 0, 1, 0.0f));

        int[] drops = {4, 8, 13};
        for (int i = 0; i < drops.length; i++) {
            int x = 24 + i * 5;
            b.fill(x - 1, 0, 0, x + 1, drops[i] - 1, 3, STONE);
            stations.put("drop_" + drops[i], b.station("drop_" + drops[i], x, drops[i], 1, 0.0f));
        }

        int duct = 42;
        b.fill(duct - 1, 0, 9, duct - 1, 1, 14, STONE);
        b.fill(duct + 1, 0, 9, duct + 1, 1, 14, STONE);
        b.fill(duct - 1, 1, 9, duct + 1, 1, 14, STONE);
        stations.put("duct", b.station("duct", duct, 0, 1, 0.0f));

        int[] widths = {2, 3, 4};
        int x = 48;
        for (int width : widths) {
            b.fill(x - 1, 0, 3, x - 1, 11, 7, STONE);
            b.fill(x + width, 0, 3, x + width, 11, 7, STONE);
            stations.put("shaft_" + width, b.station("shaft_" + width, x, 0, 5, -90.0f));
            x += width + 3;
        }
        int wall = x + 2;
        b.fill(wall - 1, 0, 3, wall - 1, 11, 7, STONE);
        stations.put("single_wall", b.station("single_wall", wall, 0, 5, 90.0f));
        return new Course(origin.immutable(), Map.copyOf(stations));
    }

    private record Builder(ServerLevel level, BlockPos origin) {

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                    for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        level.setBlock(pos, state, FLAGS);
                    }
                }
            }
        }

        Station station(String name, int x, int y, int z, float yaw) {
            return new Station(name, origin.offset(x, y, z), yaw);
        }
    }
}
