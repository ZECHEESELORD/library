package sh.harold.creative.library.sound;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public sealed interface SoundTarget permits SoundTarget.AudienceTarget, SoundTarget.EmitterTarget, SoundTarget.PositionedTarget {

    void play(Sound sound);

    static SoundTarget audience(Audience audience) {
        return new AudienceTarget(audience);
    }

    static SoundTarget emitter(SoundEmitter emitter) {
        return new EmitterTarget(emitter);
    }

    static SoundTarget positioned(SpaceId spaceId, Vec3 position, PositionedSoundEmitter emitter) {
        return new PositionedTarget(spaceId, position, emitter);
    }

    record AudienceTarget(Audience audience) implements SoundTarget {

        public AudienceTarget {
            audience = Objects.requireNonNull(audience, "audience");
        }

        @Override
        public void play(Sound sound) {
            audience.playSound(Objects.requireNonNull(sound, "sound"));
        }
    }

    record EmitterTarget(SoundEmitter emitter) implements SoundTarget {

        public EmitterTarget {
            emitter = Objects.requireNonNull(emitter, "emitter");
        }

        @Override
        public void play(Sound sound) {
            emitter.play(Objects.requireNonNull(sound, "sound"));
        }
    }

    record PositionedTarget(SpaceId spaceId, Vec3 position, PositionedSoundEmitter emitter) implements SoundTarget {

        public PositionedTarget {
            spaceId = Objects.requireNonNull(spaceId, "spaceId");
            position = Objects.requireNonNull(position, "position");
            emitter = Objects.requireNonNull(emitter, "emitter");
        }

        @Override
        public void play(Sound sound) {
            emitter.play(spaceId, position, Objects.requireNonNull(sound, "sound"));
        }
    }

    @FunctionalInterface
    interface SoundEmitter {

        void play(Sound sound);
    }

    @FunctionalInterface
    interface PositionedSoundEmitter {

        void play(SpaceId spaceId, Vec3 position, Sound sound);
    }
}
