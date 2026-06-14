package sh.harold.library.entity.house;

import sh.harold.library.entity.EntityTransform;

public interface HousePresentationRenderer extends AutoCloseable {

    void teleport(EntityTransform transform);

    @Override
    void close();
}
