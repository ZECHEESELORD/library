package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.ManagedEntity;

import java.util.Optional;
import java.util.UUID;

public interface LeashCapable {

    Optional<UUID> leashHolder();

    boolean leashHolder(ManagedEntity entity);

    void clearLeash();
}
