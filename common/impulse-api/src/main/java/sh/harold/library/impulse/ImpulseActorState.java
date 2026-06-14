package sh.harold.library.impulse;

import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.Vec3;

import java.util.Objects;

public record ImpulseActorState(Vec3 position, Frame3 lookFrame) {

    public ImpulseActorState {
        position = Objects.requireNonNull(position, "position");
        lookFrame = Objects.requireNonNull(lookFrame, "lookFrame");
    }
}
