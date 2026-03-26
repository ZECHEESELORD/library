package sh.harold.creative.library.entity.house;

import sh.harold.creative.library.entity.EntityTransform;

public interface HousePresentationRenderer extends AutoCloseable {

    void teleport(EntityTransform transform);

    @Override
    void close();
}
