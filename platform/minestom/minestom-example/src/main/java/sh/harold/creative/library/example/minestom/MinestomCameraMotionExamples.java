package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.camera.BlendMode;
import sh.harold.creative.library.camera.CameraMotion;
import sh.harold.creative.library.camera.CameraMotions;
import sh.harold.creative.library.camera.EaseOutCurve;
import sh.harold.creative.library.camera.Waveform;
import sh.harold.creative.library.camera.minestom.MinestomCameraMotionPlatform;
import sh.harold.creative.library.message.Message;

final class MinestomCameraMotionExamples {

    private static final long ALL_VARIANT_INTERVAL_TICKS = 18L;

    private final MinestomCameraMotionPlatform camera;
    private final MinestomDevHarnessMessages feedback;

    MinestomCameraMotionExamples(MinestomCameraMotionPlatform camera, MinestomDevHarnessMessages feedback) {
        this.camera = camera;
        this.feedback = feedback;
    }

    void playAll(Player player) {
        feedback.info(
                player,
                "Playing camera motion presets in order: {order}.",
                Message.slot("order", "recoil, rumble, concussion, stagger, cinematic")
        );
        ExampleVariant[] values = ExampleVariant.values();
        for (int index = 0; index < values.length; index++) {
            ExampleVariant variant = values[index];
            MinecraftServer.getSchedulerManager().buildTask(() -> play(player, variant))
                    .delay(TaskSchedule.tick(Math.toIntExact(index * ALL_VARIANT_INTERVAL_TICKS)))
                    .schedule();
        }
    }

    void playVariant(Player player, String variantName) {
        ExampleVariant variant = ExampleVariant.fromName(variantName);
        if (variant == null) {
            feedback.error(player, "Unknown camera motion preset {variant}.", Message.slot("variant", variantName));
            return;
        }
        play(player, variant);
        feedback.success(player, "Played camera motion preset {preset}.", Message.slot("preset", variantName));
    }

    void stop(Player player) {
        camera.stopAll(player);
        feedback.info(player, "Stopped active camera motion presets.");
    }

    private void play(Player player, ExampleVariant variant) {
        camera.start(player, variant.motion());
    }

    private enum ExampleVariant {
        RECOIL("recoil", CameraMotions.motion(
                Key.key("example", "camera/recoil"),
                BlendMode.ADD,
                CameraMotions.axis(0.20, 5L, 0L, Waveform.SINE),
                CameraMotions.axis(-2.20, 1L, 0L, Waveform.IMPULSE),
                CameraMotions.easeOut(6L, 1.0, EaseOutCurve.CUBIC)
        )),
        RUMBLE("rumble", CameraMotions.motion(
                Key.key("example", "camera/rumble"),
                BlendMode.ADD,
                CameraMotions.axis(0.65, 6L, 0L, Waveform.SINE, 17L),
                CameraMotions.axis(0.35, 4L, 2L, Waveform.COSINE, 29L),
                CameraMotions.constant(40L, 0.60)
        )),
        CONCUSSION("concussion", CameraMotions.motion(
                Key.key("example", "camera/concussion"),
                BlendMode.MAX,
                CameraMotions.axis(1.60, 7L, 0L, Waveform.NOISE, 41L),
                CameraMotions.axis(-3.25, 1L, 0L, Waveform.IMPULSE),
                CameraMotions.attackHoldRelease(1L, 3L, 10L, 1.0)
        )),
        STAGGER("stagger", CameraMotions.motion(
                Key.key("example", "camera/stagger"),
                BlendMode.MAX,
                CameraMotions.axis(1.80, 10L, 0L, Waveform.SAW),
                CameraMotions.axis(0.90, 8L, 2L, Waveform.TRIANGLE),
                CameraMotions.attackHoldRelease(2L, 4L, 8L, 0.85)
        )),
        CINEMATIC("cinematic", CameraMotions.motion(
                Key.key("example", "camera/cinematic"),
                BlendMode.ADD,
                CameraMotions.axis(1.10, 36L, 0L, Waveform.SINE),
                CameraMotions.axis(0.45, 24L, 6L, Waveform.COSINE),
                CameraMotions.linearDecay(80L, 0.80, 0.20)
        ));

        private final String name;
        private final CameraMotion motion;

        ExampleVariant(String name, CameraMotion motion) {
            this.name = name;
            this.motion = motion;
        }

        CameraMotion motion() {
            return motion;
        }

        private static ExampleVariant fromName(String name) {
            for (ExampleVariant variant : values()) {
                if (variant.name.equals(name)) {
                    return variant;
                }
            }
            return null;
        }
    }
}
