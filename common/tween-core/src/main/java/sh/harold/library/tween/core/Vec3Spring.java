package sh.harold.library.tween.core;

import sh.harold.library.spatial.Vec3;
import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.spring.StatefulSampler;

/**
 * Three independent {@link Spring}s, one per axis, sharing a spec.
 */
public final class Vec3Spring implements StatefulSampler<Vec3> {

    private final Spring x;
    private final Spring y;
    private final Spring z;

    public Vec3Spring(SpringSpec spec, Vec3 initial) {
        this.x = new Spring(spec, initial.x());
        this.y = new Spring(spec, initial.y());
        this.z = new Spring(spec, initial.z());
    }

    public Vec3 position() {
        return new Vec3(x.position(), y.position(), z.position());
    }

    public Vec3 velocity() {
        return new Vec3(x.velocity(), y.velocity(), z.velocity());
    }

    public Vec3 target() {
        return new Vec3(x.target(), y.target(), z.target());
    }

    public void retarget(Vec3 target) {
        x.retarget(target.x());
        y.retarget(target.y());
        z.retarget(target.z());
    }

    public void kickVelocity(Vec3 perTick) {
        x.kickVelocity(perTick.x());
        y.kickVelocity(perTick.y());
        z.kickVelocity(perTick.z());
    }

    public void kickDisplacement(Vec3 delta) {
        x.kickDisplacement(delta.x());
        y.kickDisplacement(delta.y());
        z.kickDisplacement(delta.z());
    }

    @Override
    public void tick() {
        x.tick();
        y.tick();
        z.tick();
    }

    @Override
    public Vec3 sample(double partial) {
        return new Vec3(x.sampleDouble(partial), y.sampleDouble(partial), z.sampleDouble(partial));
    }

    @Override
    public void reset(Vec3 value) {
        x.reset(value.x());
        y.reset(value.y());
        z.reset(value.z());
    }
}
