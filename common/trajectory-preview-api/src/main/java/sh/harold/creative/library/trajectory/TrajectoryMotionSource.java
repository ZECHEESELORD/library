package sh.harold.creative.library.trajectory;

@FunctionalInterface
public interface TrajectoryMotionSource {

    TrajectoryMotion currentMotion();
}
