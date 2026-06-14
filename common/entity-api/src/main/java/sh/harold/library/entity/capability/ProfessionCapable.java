package sh.harold.library.entity.capability;

import net.kyori.adventure.key.Key;

import java.util.Optional;

public interface ProfessionCapable {

    Optional<Key> profession();

    void profession(Key profession);

    void clearProfession();
}
