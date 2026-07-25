package sh.harold.library.npc.behavior;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An equal-probability set of positional sound variants.
 */
public final class NpcSoundProfile {
    private final List<Variant> variants;

    private NpcSoundProfile(List<Variant> variants) {
        this.variants = List.copyOf(variants);
    }

    public List<Variant> variants() {
        return variants;
    }

    public boolean silent() {
        return variants.isEmpty();
    }

    public static NpcSoundProfile silentProfile() {
        return new NpcSoundProfile(List.of());
    }

    public static NpcSoundProfile of(Variant variant, Variant... additionalVariants) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(additionalVariants, "additionalVariants");
        List<Variant> variants = new ArrayList<>(1 + additionalVariants.length);
        variants.add(variant);
        for (Variant additional : additionalVariants) {
            variants.add(Objects.requireNonNull(additional, "additionalVariants contains null"));
        }
        return new NpcSoundProfile(variants);
    }

    public static Builder builder() {
        return new Builder();
    }

    public record Variant(
            Key key,
            Sound.Source source,
            float volume,
            float minimumPitch,
            float maximumPitch
    ) {
        public Variant {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(source, "source");
            requireFiniteNonNegative(volume, "volume");
            requireFinitePositive(minimumPitch, "minimumPitch");
            requireFinitePositive(maximumPitch, "maximumPitch");
            if (minimumPitch > maximumPitch) {
                throw new IllegalArgumentException("minimumPitch cannot exceed maximumPitch");
            }
        }

        public Variant(Key key, Sound.Source source, float volume, float pitch) {
            this(key, source, volume, pitch, pitch);
        }

        private static void requireFiniteNonNegative(float value, String name) {
            if (!Float.isFinite(value) || value < 0.0f) {
                throw new IllegalArgumentException(name + " must be finite and non-negative");
            }
        }

        private static void requireFinitePositive(float value, String name) {
            if (!Float.isFinite(value) || value <= 0.0f) {
                throw new IllegalArgumentException(name + " must be finite and positive");
            }
        }
    }

    public static final class Builder {
        private final List<Variant> variants = new ArrayList<>();

        private Builder() {
        }

        public Builder sound(Key key, Sound.Source source, float volume, float pitch) {
            return variant(new Variant(key, source, volume, pitch));
        }

        public Builder sound(
                Key key,
                Sound.Source source,
                float volume,
                float minimumPitch,
                float maximumPitch
        ) {
            return variant(new Variant(key, source, volume, minimumPitch, maximumPitch));
        }

        public Builder variant(Variant variant) {
            variants.add(Objects.requireNonNull(variant, "variant"));
            return this;
        }

        public NpcSoundProfile build() {
            return new NpcSoundProfile(variants);
        }
    }
}
