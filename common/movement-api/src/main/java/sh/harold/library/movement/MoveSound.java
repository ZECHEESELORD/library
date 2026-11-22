package sh.harold.library.movement;

import sh.harold.library.spatial.Vec3;

import java.util.Objects;

/**
 * A sound a move asks for. The host resolves block sounds against the block at {@code at}.
 *
 * @param source  what to play
 * @param soundId the sound event id for {@link Source#EVENT}, for example {@code minecraft:entity.breeze.jump}; also
 *                the fallback when a block sound cannot be resolved
 * @param at      where it plays and, for block sounds, which block
 * @param volume  volume
 * @param pitch   pitch
 */
public record MoveSound(Source source, String soundId, Vec3 at, float volume, float pitch) {

    public MoveSound {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(soundId, "soundId");
        Objects.requireNonNull(at, "at");
    }

    public static MoveSound event(String soundId, Vec3 at, double volume, double pitch) {
        return new MoveSound(Source.EVENT, soundId, at, (float) volume, (float) pitch);
    }

    public enum Source {
        /** A named sound event. */
        EVENT,
        /** The step sound of the block at {@code at}. */
        BLOCK_STEP,
        /** The hit sound of the block at {@code at}. */
        BLOCK_HIT
    }
}
