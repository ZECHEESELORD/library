package sh.harold.creative.library.example.paper;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import sh.harold.creative.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;

final class PaperPrimitiveDebugRenderer {

    void point(Iterable<? extends Player> viewers, Vec3 point, int rgb, float scale) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb), Math.max(0.2f, scale));
        for (Player viewer : viewers) {
            if (viewer.isOnline()) {
                viewer.spawnParticle(Particle.DUST, point.x(), point.y(), point.z(), 1, 0.0, 0.0, 0.0, 0.0, dust);
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
