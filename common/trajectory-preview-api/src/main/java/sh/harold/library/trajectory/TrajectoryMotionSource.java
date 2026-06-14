package sh.harold.library.trajectory;

@FunctionalInterface
public interface TrajectoryMotionSource {

    TrajectoryMotion currentMotion();
}
