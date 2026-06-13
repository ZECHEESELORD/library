package sh.harold.creative.library.scoreboard;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.Optional;
import java.util.UUID;

public interface ScoreboardService extends AutoCloseable {

    void register(ScoreboardSpec spec);

    void unregister(Key key);

    void show(UUID viewerId, Key scoreboardKey);

    void hide(UUID viewerId);

    void refresh(UUID viewerId);

    void clearViewer(UUID viewerId);

    void overrideSection(UUID viewerId, String sectionId, ScoreboardSection replacement);

    void clearSectionOverride(UUID viewerId, String sectionId);

    void hideSection(UUID viewerId, String sectionId);

    void showSection(UUID viewerId, String sectionId);

    KeyedHandle pushTransient(UUID viewerId, TransientSectionSpec spec);

    void clearTransient(UUID viewerId, Key key);

    void clearTransients(UUID viewerId);

    void advance();

    Optional<ScoreboardFrame> render(UUID viewerId);

    @Override
    void close();
}
