package sh.harold.library.npc.behavior.core;

import java.util.Objects;
import java.util.random.RandomGenerator;

public interface NpcBehaviorRandom {

    int nextInt(int originInclusive, int boundExclusive);

    double nextDouble();

    default int betweenInclusive(int minimum, int maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum must not exceed maximum");
        }
        return nextInt(minimum, Math.addExact(maximum, 1));
    }

    static NpcBehaviorRandom from(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        return new NpcBehaviorRandom() {
            @Override
            public int nextInt(int originInclusive, int boundExclusive) {
                return random.nextInt(originInclusive, boundExclusive);
            }

            @Override
            public double nextDouble() {
                return random.nextDouble();
            }
        };
    }
}
