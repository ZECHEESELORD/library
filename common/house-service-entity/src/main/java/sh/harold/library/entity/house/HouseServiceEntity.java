package sh.harold.library.entity.house;

import sh.harold.library.entity.EntityTransform;
import sh.harold.library.entity.ManagedEntity;

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
