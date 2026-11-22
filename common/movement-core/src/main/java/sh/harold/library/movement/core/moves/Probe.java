package sh.harold.library.movement.core.moves;

import sh.harold.library.movement.Aabb;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.MovementWorld;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** World probes the stock moves share: ledges in front, walls at the sides, headroom. Host-free geometry. */
public final class Probe {

    private static final double EPSILON = 1.0e-4;
    static final List<Vec3> CARDINALS = List.of(new Vec3(1, 0, 0), new Vec3(-1, 0, 0), new Vec3(0, 0, 1),
            new Vec3(0, 0, -1));

    private Probe() {
    }

    /** The cardinal direction nearest {@code direction} (horizontal). */
    public static Vec3 cardinal(Vec3 direction) {
        if (Math.abs(direction.x()) >= Math.abs(direction.z())) {
            return new Vec3(Math.signum(direction.x()) == 0 ? 1 : Math.signum(direction.x()), 0, 0);
        }
        return new Vec3(0, 0, Math.signum(direction.z()));
    }

    /** The movement keys as a world-space horizontal unit direction, or zero. */
    public static Vec3 inputDirection(BodyState body, InputFrame input) {
        Vec3 direction = body.facing().multiply(input.forwardImpulse())
                .subtract(body.right().multiply(input.strafeImpulse()));
        return direction.lengthSquared() < 1.0e-9 ? Vec3.ZERO : direction.normalize();
    }

    /** A ledge in front: its top, and the gap from the hitbox's front face to the wall. */
    public record Ledge(Vec3 direction, double top, double gap, double height) {
    }

    /**
     * Finds a ledge along {@code direction} (cardinal) within {@code reach} of the hitbox's front, with its top between
     * {@code minHeight} and {@code maxHeight} above the feet, clear space of {@code headroom} above it and
     * {@code depth} deep, and room for the body to rise in place to the top.
     */
    public static Optional<Ledge> ledge(MovementWorld world, BodyState body, Vec3 direction, double reach,
                                        double minHeight, double maxHeight, double headroom, double depth) {
        Aabb box = body.box();
        double feet = box.minY();
        Aabb strip = front(box, direction, reach, feet + EPSILON, feet + maxHeight + 0.5);
        List<Aabb> hits = world.collisions(strip);
        if (hits.isEmpty()) {
            return Optional.empty();
        }
        double top = Double.NEGATIVE_INFINITY;
        double gap = Double.POSITIVE_INFINITY;
        for (Aabb hit : hits) {
            if (hit.maxY() <= feet + EPSILON) {
                continue;
            }
            top = Math.max(top, Math.min(hit.maxY(), strip.maxY()));
            gap = Math.min(gap, faceGap(box, hit, direction));
        }
        if (top == Double.NEGATIVE_INFINITY) {
            return Optional.empty();
        }
        double height = top - feet;
        if (height < minHeight - EPSILON || height > maxHeight + EPSILON) {
            return Optional.empty();
        }
        Aabb above = front(box, direction, depth, top + EPSILON, top + headroom);
        Aabb onTop = shift(above, direction, Math.max(gap, 0.0));
        if (!world.free(onTop)) {
            return Optional.empty();
        }
        Aabb raised = new Aabb(box.minX(), top + EPSILON, box.minZ(), box.maxX(), top + body.height(), box.maxZ());
        if (!world.free(raised)) {
            return Optional.empty();
        }
        return Optional.of(new Ledge(direction, top, Math.max(gap, 0.0), height));
    }

    /** A wall touching the body's side. */
    public record Wall(Vec3 normal, double coverage, Vec3 contact) {
    }

    /**
     * The best wall within {@code reach} of the hitbox's sides that covers at least {@code minHeight} of the body's
     * height (or all of it, for a body shorter than that).
     */
    public static Optional<Wall> wall(MovementWorld world, BodyState body, double reach, double minHeight) {
        Aabb box = body.box();
        List<Wall> walls = new ArrayList<>();
        for (Vec3 direction : CARDINALS) {
            Aabb strip = front(box, direction, reach, box.minY(), box.maxY());
            List<Aabb> hits = world.collisions(strip);
            if (hits.isEmpty()) {
                continue;
            }
            double coverage = coverage(hits, box.minY(), box.maxY());
            if (coverage + EPSILON >= Math.min(minHeight, box.height())) {
                Aabb nearest = hits.stream().min(Comparator.comparingDouble(hit -> faceGap(box, hit, direction)))
                        .orElseThrow();
                Vec3 center = box.bottomCenter();
                Vec3 contact = new Vec3(
                        direction.x() == 0 ? center.x() : (direction.x() > 0 ? nearest.minX() + 0.01 : nearest.maxX() - 0.01),
                        center.y() + 0.9,
                        direction.z() == 0 ? center.z() : (direction.z() > 0 ? nearest.minZ() + 0.01 : nearest.maxZ() - 0.01));
                walls.add(new Wall(direction.multiply(-1.0), coverage, contact));
            }
        }
        return walls.stream().max(Comparator.comparingDouble(Wall::coverage));
    }

    /** Whether a standing body ({@code height} tall) fits at the body's position. */
    public static boolean fits(MovementWorld world, BodyState body, double height) {
        Aabb standing = Aabb.ofBody(body.position(), body.width(), height).inflate(-1.0e-3);
        return world.free(standing);
    }

    static Aabb front(Aabb box, Vec3 direction, double reach, double minY, double maxY) {
        if (direction.x() > 0) {
            return new Aabb(box.maxX(), minY, box.minZ(), box.maxX() + reach, maxY, box.maxZ());
        } else if (direction.x() < 0) {
            return new Aabb(box.minX() - reach, minY, box.minZ(), box.minX(), maxY, box.maxZ());
        } else if (direction.z() > 0) {
            return new Aabb(box.minX(), minY, box.maxZ(), box.maxX(), maxY, box.maxZ() + reach);
        }
        return new Aabb(box.minX(), minY, box.minZ() - reach, box.maxX(), maxY, box.minZ());
    }

    private static Aabb shift(Aabb box, Vec3 direction, double distance) {
        return box.move(direction.multiply(distance));
    }

    static double faceGap(Aabb box, Aabb hit, Vec3 direction) {
        if (direction.x() > 0) {
            return hit.minX() - box.maxX();
        } else if (direction.x() < 0) {
            return box.minX() - hit.maxX();
        } else if (direction.z() > 0) {
            return hit.minZ() - box.maxZ();
        }
        return box.minZ() - hit.maxZ();
    }

    private static double coverage(List<Aabb> hits, double from, double to) {
        List<double[]> spans = new ArrayList<>();
        for (Aabb hit : hits) {
            double low = Math.max(from, hit.minY());
            double high = Math.min(to, hit.maxY());
            if (high > low) {
                spans.add(new double[]{low, high});
            }
        }
        spans.sort(Comparator.comparingDouble(span -> span[0]));
        double total = 0.0;
        double reached = from;
        for (double[] span : spans) {
            double low = Math.max(span[0], reached);
            if (span[1] > low) {
                total += span[1] - low;
                reached = span[1];
            }
        }
        return total;
    }
}
