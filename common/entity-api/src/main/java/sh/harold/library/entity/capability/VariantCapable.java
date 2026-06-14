package sh.harold.library.entity.capability;

import net.kyori.adventure.key.Key;

import java.util.Optional;

public interface VariantCapable {

    Optional<Key> variant();

    void variant(Key variant);

    void clearVariant();
}
