package sh.harold.creative.library.entity.house;

import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.ManagedEntity;

import java.util.Objects;

public final class StandardHouseServiceEntity implements HouseServiceEntity {

    private final ManagedEntity entity;
    private final HousePresentation presentation;
    private final HousePresentationRenderer renderer;

    public StandardHouseServiceEntity(ManagedEntity entity, HousePresentation presentation, HousePresentationRenderer renderer) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.presentation = Objects.requireNonNull(presentation, "presentation");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public ManagedEntity entity() {
        return entity;
    }

    @Override
    public HousePresentation presentation() {
        return presentation;
    }

    @Override
    public void teleport(EntityTransform transform) {
        entity.teleport(transform);
        renderer.teleport(transform);
    }

    @Override
    public void despawn() {
        try {
            renderer.close();
        } finally {
            entity.despawn();
        }
    }
}
