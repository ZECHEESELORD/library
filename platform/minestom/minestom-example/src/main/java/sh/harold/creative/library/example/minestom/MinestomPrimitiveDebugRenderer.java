package sh.harold.creative.library.example.minestom;

import net.minestom.server.color.Color;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import sh.harold.creative.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;

final class MinestomPrimitiveDebugRenderer {

    void point(Iterable<? extends Player> viewers, Vec3 point, int rgb, float scale) {
        ParticlePacket packet = new ParticlePacket(
                Particle.DUST.withProperties(new Color(rgb), Math.max(0.2f, scale)),
                point.x(),
                point.y(),
                point.z(),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1
        );
        for (Player viewer : viewers) {
            if (viewer.isOnline()) {
                viewer.sendPacket(packet);
            }
        }
    }

    void points(Iterable<? extends Player> viewers, Iterable<Vec3> points, int rgb, float scale) {
        for (Vec3 point : points) {
            point(viewers, point, rgb, scale);
        }
    }

    void polyline(Iterable<? extends Player> viewers, List<Vec3> points, double step, int rgb, float scale) {
        points(viewers, densify(points, step), rgb, scale);
    }

    List<Vec3> densify(List<Vec3> points, double step) {
        List<Vec3> dense = new ArrayList<>();
        if (points.isEmpty()) {
            return dense;
        }
        dense.add(points.getFirst());
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 start = points.get(i);
            Vec3 end = points.get(i + 1);
            double distance = start.distance(end);
            int samples = Math.max(1, (int) Math.ceil(distance / Math.max(0.1, step)));
            for (int sample = 1; sample <= samples; sample++) {
                double progress = sample / (double) samples;
                dense.add(start.add(end.subtract(start).multiply(progress)));
            }
        }
        return dense;
    }
}
