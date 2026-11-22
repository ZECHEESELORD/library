package sh.harold.library.camera.rig.core;

import sh.harold.library.camera.rig.CameraState;
import sh.harold.library.camera.rig.LayerState;
import sh.harold.library.camera.rig.stage.AdditiveStage;
import sh.harold.library.camera.rig.stage.AimStage;
import sh.harold.library.camera.rig.stage.AnchorStage;
import sh.harold.library.camera.rig.stage.BodyStage;
import sh.harold.library.camera.rig.stage.ConstraintStage;
import sh.harold.library.camera.rig.stage.LayerSample;
import sh.harold.library.camera.rig.stage.LensStage;
import sh.harold.library.camera.rig.stage.ProjectionStage;
import sh.harold.library.camera.rig.stage.RigFrame;
import sh.harold.library.spatial.Vec3;

import java.util.Objects;

/**
 * The one composer (architecture 7.3): anchor, body, aim and lens build the shot; stage 5 samples the layers; stage 7
 * constrains them; the layers compose onto the shot as local pitch, then local roll, plus FOV and eye height; stage 6
 * changes the projection; stage 7 takes a last pass. With identity layers the shot comes back unchanged, bit for bit.
 */
public final class CameraPipeline {

    private final AnchorStage anchor;
    private final BodyStage body;
    private final AimStage aim;
    private final LensStage lens;
    private final AdditiveStage additive;
    private final ProjectionStage projection;
    private final ConstraintStage constraints;

    public CameraPipeline(AnchorStage anchor, BodyStage body, AimStage aim, LensStage lens, AdditiveStage additive,
                          ProjectionStage projection, ConstraintStage constraints) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.body = Objects.requireNonNull(body, "body");
        this.aim = Objects.requireNonNull(aim, "aim");
        this.lens = Objects.requireNonNull(lens, "lens");
        this.additive = Objects.requireNonNull(additive, "additive");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.constraints = Objects.requireNonNull(constraints, "constraints");
    }

    /** Wave 1's first-person stack: eye anchor, pass-through stages 2, 3, 4 and 6. */
    public static CameraPipeline firstPerson(AdditiveStage additive, ConstraintStage constraints) {
        return new CameraPipeline(AnchorStage.EYE, BodyStage.PASS_THROUGH, AimStage.PASS_THROUGH,
                LensStage.PASS_THROUGH, additive, ProjectionStage.PASS_THROUGH, constraints);
    }

    public CameraState evaluate(RigFrame frame) {
        CameraState shot = anchor.anchor(frame);
        shot = body.apply(shot, frame);
        shot = aim.apply(shot, frame);
        shot = lens.apply(shot, frame);
        LayerSample sample = additive.sample(frame);
        LayerState layers = constraints.constrainLayers(sample, shot, frame);
        CameraState composed = compose(shot, layers);
        composed = projection.apply(composed, frame);
        return constraints.constrainState(composed, frame);
    }

    static CameraState compose(CameraState shot, LayerState layers) {
        if (layers.isIdentity()) {
            return shot.withLayerRecord(layers);
        }
        Vec3 position = layers.eyeHeight() == 0.0
                ? shot.position()
                : shot.position().add(new Vec3(0.0, layers.eyeHeight(), 0.0));
        return shot.withLayers(layers, shot.fovDegrees() + layers.fovAdd(),
                shot.eyeHeightOffset() + layers.eyeHeight(), position);
    }
}
