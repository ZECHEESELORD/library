package sh.harold.creative.library.entity.house;

import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.ManagedEntity;

public interface HouseServiceEntity extends AutoCloseable {

    ManagedEntity entity();

    HousePresentation presentation();

    void teleport(EntityTransform transform);

    void despawn();

    @Override
    default void close() {
        despawn();
    }
}
