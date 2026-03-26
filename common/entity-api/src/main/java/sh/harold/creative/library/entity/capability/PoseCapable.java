package sh.harold.creative.library.entity.capability;

import sh.harold.creative.library.entity.EntityPose;

public interface PoseCapable {

    EntityPose pose();

    void pose(EntityPose pose);
}
