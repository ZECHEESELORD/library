package sh.harold.library.movement;

import net.kyori.adventure.key.Key;

import java.util.Set;

/**
 * The movement feature on one side, as a disposable handle. Closing it unhooks everything it installed and the body
 * moves exactly like vanilla again. With no move enabled it already does.
 */
public interface MovementService extends AutoCloseable {

    Set<Key> enabledMoves();

    void setEnabled(Key move, boolean enabled);

    default boolean enabled(Key move) {
        return enabledMoves().contains(move);
    }

    JumpPreset jumpPreset();

    void setJumpPreset(JumpPreset preset);

    SlideVariant slideVariant();

    void setSlideVariant(SlideVariant variant);

    boolean active();

    @Override
    void close();
}
