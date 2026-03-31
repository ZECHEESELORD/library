package sh.harold.creative.library.impulse;

import sh.harold.creative.library.spatial.Frame3;
import sh.harold.creative.library.spatial.Vec3;

import java.util.Objects;

public record ImpulseActorState(Vec3 position, Frame3 lookFrame) {

    public ImpulseActorState {
        position = Objects.requireNonNull(position, "position");
        lookFrame = Objects.requireNonNull(lookFrame, "lookFrame");
    }
}
