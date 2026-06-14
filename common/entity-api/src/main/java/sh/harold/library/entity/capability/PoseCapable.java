package sh.harold.library.entity.capability;

import sh.harold.library.entity.EntityPose;

public interface PoseCapable {

    EntityPose pose();

    void pose(EntityPose pose);
}
