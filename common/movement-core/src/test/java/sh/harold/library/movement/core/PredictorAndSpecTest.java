package sh.harold.library.movement.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.movement.BodyState;
import sh.harold.library.movement.InputFrame;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.movement.core.tuning.JsonText;
import sh.harold.library.movement.core.tuning.Moveset;
import sh.harold.library.movement.core.tuning.MovesetSpec;
import sh.harold.library.reload.core.JsonReader;
import sh.harold.library.spatial.Vec3;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictorAndSpecTest {

    static final Moveset SLIDE = Moveset.DEFAULT.withEnabled(Set.of(MoveKeys.SLIDE, MoveKeys.JUMP));

    @Test
    void rejectedSlideReplaysAsIfItNeverStarted() {
        Driver live = Driver.standing(SLIDE, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        Driver honest = Driver.standing(SLIDE.withEnabled(Set.of(MoveKeys.JUMP)), TestWorld.flat(),
                new Vec3(0.5, 0.0, 0.5));
        MovementPredictor predictor = new MovementPredictor(live.simulation);
        long slideStart = -1;
        for (int i = 0; i < 26; i++) {
            InputFrame input = Driver.keys(i < 15 ? "FS" : "FSN");
            long tick = live.tick;
            live.tick(input);
            honest.tick(input);
            predictor.record(tick, input, live.snapshot.copy());
            if (slideStart < 0 && live.firstStart(MoveKeys.SLIDE).isPresent()) {
                slideStart = live.firstStart(MoveKeys.SLIDE).get().tick();
            }
        }
        assertTrue(slideStart > 0, "the slide started");
        assertTrue(live.body().height() < 1.0, "sliding low before the verdict");
        var result = predictor.reject(MoveKeys.SLIDE, slideStart, live.tick + 20, TestWorld.flat()).orElseThrow();
        assertEquals(live.tick - slideStart, result.replayedTicks());
        assertEquals(honest.body().position(), result.current().body().position());
        assertEquals(BodyState.PLAYER_HEIGHT, result.current().body().height(), 1.0e-9);
        assertTrue(result.current().movement().active().isEmpty());
        assertTrue(result.predictedBefore().body().position().z() != result.current().body().position().z());
    }

    @Test
    void tooOldClaimOnlyEndsTheMove() {
        Driver live = Driver.standing(SLIDE, TestWorld.flat(), new Vec3(0.5, 0.0, 0.5));
        MovementPredictor predictor = new MovementPredictor(live.simulation);
        for (int i = 0; i < 80; i++) {
            long tick = live.tick;
            live.tick(Driver.keys("F"));
            predictor.record(tick, Driver.keys("F"), live.snapshot.copy());
        }
        assertTrue(predictor.reject(MoveKeys.SLIDE, 2, 200, TestWorld.flat()).isEmpty());
    }

    @Test
    void keyLettersParseAndRoundTrip() {
        assertEquals(Driver.keys("FSN"), InputFrame.keys("FSN"));
        for (String letters : new String[]{"", "F", "BLRJ", "FBLRJNS"}) {
            InputFrame frame = InputFrame.keys(letters);
            assertEquals(frame, InputFrame.keys(frame.compact()));
        }
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> InputFrame.keys("FX"));
    }

    @Test
    void specSwitchesOverrideTheFilesAndTextsRoundTrip() {
        MovesetSpec spec = MovesetSpec.DEFAULT.withJumpPreset(JumpPreset.C).withSlideVariant(SlideVariant.S2)
                .withMove(MoveKeys.MANTLE, false);
        Moveset moveset = spec.moveset();
        assertEquals(JumpPreset.C, moveset.jump().preset());
        assertEquals(SlideVariant.S2, moveset.slide().variant());
        assertTrue(!moveset.enables(MoveKeys.MANTLE));
        assertTrue(MovesetSpec.none().moveset().enabled().isEmpty());
        for (String file : Moveset.FILES) {
            String compact = JsonText.write(JsonReader.parse(Moveset.defaultText(file)));
            MovesetSpec rewritten = MovesetSpec.DEFAULT.withText(file, compact);
            assertEquals(MovesetSpec.DEFAULT.moveset(), rewritten.moveset(), file);
        }
    }
}
