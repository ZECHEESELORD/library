package sh.harold.library.camera.rig.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The shake bus: trauma kicks that decay linearly and cap at 1. Trauma is a pure function of time and the kick list,
 * {@code min(1, sum(max(0, amount - decay * (t - kickTime))))} over kicks at or before {@code t}, so it seeks and samples
 * per frame exactly. Guarded by the owning rig's lock.
 */
final class TraumaBus {

    private record Kick(double time, double amount) {
    }

    private final double decayPerTick;
    private final List<Kick> kicks = new ArrayList<>();

    TraumaBus(double decayPerTick) {
        this.decayPerTick = decayPerTick;
    }

    void add(double time, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException("trauma must be finite and non-negative");
        }
        if (amount > 0.0) {
            kicks.add(new Kick(time, Math.min(1.0, amount)));
        }
    }

    double trauma(double time) {
        double sum = 0.0;
        for (Kick kick : kicks) {
            if (kick.time <= time) {
                sum += Math.max(0.0, kick.amount - (decayPerTick * (time - kick.time)));
            }
        }
        return Math.min(1.0, sum);
    }

    /** Forgets kicks fully decayed before {@code time}. */
    void prune(double time) {
        kicks.removeIf(kick -> kick.time + (kick.amount / decayPerTick) < time);
    }

    boolean isEmpty() {
        return kicks.isEmpty();
    }

    void clear() {
        kicks.clear();
    }
}
