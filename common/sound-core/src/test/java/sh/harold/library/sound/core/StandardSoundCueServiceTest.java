package sh.harold.library.sound.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;
import sh.harold.library.sound.CuePlayback;
import sh.harold.library.sound.SoundCue;
import sh.harold.library.sound.SoundCueKeys;
import sh.harold.library.sound.SoundTarget;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static sh.harold.library.sound.SoundCues.atTick;
import static sh.harold.library.sound.SoundCues.layer;
import static sh.harold.library.sound.SoundCues.oneOf;
import static sh.harold.library.sound.SoundCues.sequence;
import static sh.harold.library.sound.SoundCues.silent;
import static sh.harold.library.sound.SoundCues.sound;

class StandardSoundCueServiceTest {

    @Test
    void stockPackIsPreloadedIntoEveryService() {
        StandardSoundCueService service = new StandardSoundCueService(SoundCueScheduler.unsupported());

        SoundCue stockCue = service.registry().cue(SoundCueKeys.MENU_CLICK);

        assertEquals(SoundCue.SoundEffect.class, stockCue.getClass());
    }

    @Test
    void layeredAndSequencedCuesGroupSoundsByTickAndPreserveOrder() {
        ManualScheduler scheduler = new ManualScheduler();
        StandardSoundCueService service = new StandardSoundCueService(scheduler, deterministicRandom(1));
        RecordingAudience audience = new RecordingAudience();
        SoundCue cue = sequence(
                atTick(0, layer(
                        sound("minecraft:ui.button.click", 0.8f, 1.0f),
                        sound("minecraft:block.note_block.pling", 0.6f, 1.2f)
                )),
                atTick(3, oneOf(
                        sound("minecraft:entity.villager.no", 0.5f, 1.0f),
                        sound("minecraft:entity.experience_orb.pickup", 0.5f, 1.4f)
                )),
                atTick(3, sound("minecraft:block.amethyst_block.chime", 0.7f, 0.9f))
        );

        service.play(audience, cue);

        assertEquals(List.of(
                "minecraft:ui.button.click@0.8/1.0",
                "minecraft:block.note_block.pling@0.6/1.2"
        ), audience.played);
        assertEquals(1, scheduler.tasks.size());

        scheduler.tasks.get(0).fire();

        assertEquals(List.of(
                "minecraft:ui.button.click@0.8/1.0",
                "minecraft:block.note_block.pling@0.6/1.2",
                "minecraft:entity.experience_orb.pickup@0.5/1.4",
                "minecraft:block.amethyst_block.chime@0.7/0.9"
        ), audience.played);
    }

    @Test
    void cancelStopsFutureScheduledTicks() {
        ManualScheduler scheduler = new ManualScheduler();
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        RecordingAudience audience = new RecordingAudience();
        CuePlayback playback = service.play(audience, sequence(
                atTick(2, sound("minecraft:ui.button.click", 0.8f, 1.0f)),
                atTick(4, sound("minecraft:block.note_block.pling", 0.6f, 1.2f))
        ));

        playback.cancel();
        scheduler.tasks.forEach(ManualTask::fire);

        assertEquals(List.of(), audience.played);
        assertEquals(List.of(true, true), scheduler.cancelledStates());
    }

    @Test
    void delayedStepsAreScheduledAgainstTheirPlaybackTarget() {
        TargetAwareScheduler scheduler = new TargetAwareScheduler();
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        SoundTarget target = SoundTarget.audience(new RecordingAudience());

        service.play(target, sequence(
                atTick(2, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));

        assertSame(target, scheduler.target);
        assertEquals(2L, scheduler.delayTicks);
    }

    @Test
    void synchronousRetirementCompletesPlaybackBeforeTaskRegistration() {
        DiscardAwareScheduler scheduler = new DiscardAwareScheduler(true);
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        RecordingAudience audience = new RecordingAudience();

        service.play(SoundTarget.audience(audience), sequence(
                atTick(2, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));

        assertEquals(List.of(), audience.played);
        assertTrue(scheduler.task.cancelled);
        service.close();
        assertEquals(1, scheduler.task.cancelCalls);
    }

    @Test
    void laterRetirementDiscardsPlaybackWithoutLeavingItActive() {
        DiscardAwareScheduler scheduler = new DiscardAwareScheduler(false);
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        RecordingAudience audience = new RecordingAudience();

        service.play(SoundTarget.audience(audience), sequence(
                atTick(2, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));
        scheduler.discard();
        scheduler.fire();
        service.close();

        assertEquals(List.of(), audience.played);
        assertFalse(scheduler.task.cancelled);
    }

    @Test
    void firedDelayedStepCannotAlsoBeDiscarded() {
        DiscardAwareScheduler scheduler = new DiscardAwareScheduler(false);
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        RecordingAudience audience = new RecordingAudience();

        service.play(SoundTarget.audience(audience), sequence(
                atTick(2, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));
        scheduler.fire();
        scheduler.discard();
        scheduler.fire();
        service.close();

        assertEquals(List.of("minecraft:ui.button.click@0.8/1.0"), audience.played);
        assertFalse(scheduler.task.cancelled);
    }

    @Test
    void closeCancelsOutstandingPlaybackAndRejectsFurtherPlayRequests() {
        ManualScheduler scheduler = new ManualScheduler();
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        service.play(new RecordingAudience(), sequence(
                atTick(5, sound("minecraft:ui.button.click", 0.8f, 1.0f))
        ));

        service.close();

        assertEquals(List.of(true), scheduler.cancelledStates());
        assertThrows(IllegalStateException.class, () -> service.play(new RecordingAudience(), silent()));
    }

    @Test
    void positionedTargetsUseCustomEmitter() {
        ManualScheduler scheduler = new ManualScheduler();
        StandardSoundCueService service = new StandardSoundCueService(scheduler);
        RecordingEmitter emitter = new RecordingEmitter();
        SoundTarget target = SoundTarget.positioned(
                SpaceId.of("creative", "arena"),
                new Vec3(10.0, 64.0, -4.0),
                emitter
        );

        service.play(target, sound("minecraft:entity.experience_orb.pickup", 0.5f, 1.4f));

        assertEquals(List.of("creative:arena@10.0,64.0,-4.0=minecraft:entity.experience_orb.pickup@0.5/1.4"), emitter.played);
    }

    private static Random deterministicRandom(int fixedIndex) {
        return new Random() {
            @Override
            public int nextInt(int bound) {
                return fixedIndex;
            }
        };
    }

    private static final class RecordingAudience implements Audience {

        private final List<String> played = new ArrayList<>();

        @Override
        public void playSound(Sound sound) {
            played.add(sound.name().asString() + "@" + sound.volume() + "/" + sound.pitch());
        }
    }

    private static final class ManualScheduler implements SoundCueScheduler {

        private final List<ManualTask> tasks = new ArrayList<>();

        @Override
        public ScheduledCueTask schedule(long delayTicks, Runnable action) {
            ManualTask task = new ManualTask(delayTicks, action);
            tasks.add(task);
            return task;
        }

        List<Boolean> cancelledStates() {
            return tasks.stream().map(task -> task.cancelled).toList();
        }
    }

    private static final class ManualTask implements ScheduledCueTask {

        private final long delayTicks;
        private final Runnable action;

        private boolean cancelled;
        private boolean fired;

        private ManualTask(long delayTicks, Runnable action) {
            this.delayTicks = delayTicks;
            this.action = action;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        void fire() {
            if (cancelled || fired) {
                return;
            }
            fired = true;
            action.run();
        }
    }

    private static final class TargetAwareScheduler implements SoundCueScheduler {

        private SoundTarget target;
        private long delayTicks;

        @Override
        public ScheduledCueTask schedule(long delayTicks, Runnable action) {
            throw new AssertionError();
        }

        @Override
        public ScheduledCueTask schedule(SoundTarget target, long delayTicks, Runnable action) {
            this.target = target;
            this.delayTicks = delayTicks;
            return () -> {
            };
        }
    }

    private static final class DiscardAwareScheduler implements SoundCueScheduler {

        private final boolean discardSynchronously;
        private final CountingTask task = new CountingTask();

        private Runnable action;
        private Runnable onDiscard;

        private DiscardAwareScheduler(boolean discardSynchronously) {
            this.discardSynchronously = discardSynchronously;
        }

        @Override
        public ScheduledCueTask schedule(long delayTicks, Runnable action) {
            throw new AssertionError();
        }

        @Override
        public ScheduledCueTask schedule(
                SoundTarget target,
                long delayTicks,
                Runnable action,
                Runnable onDiscard
        ) {
            this.action = action;
            this.onDiscard = onDiscard;
            if (discardSynchronously) {
                onDiscard.run();
            }
            return task;
        }

        void fire() {
            action.run();
        }

        void discard() {
            onDiscard.run();
        }
    }

    private static final class CountingTask implements ScheduledCueTask {

        private boolean cancelled;
        private int cancelCalls;

        @Override
        public void cancel() {
            cancelled = true;
            cancelCalls++;
        }
    }

    private static final class RecordingEmitter implements SoundTarget.PositionedSoundEmitter {

        private final List<String> played = new ArrayList<>();

        @Override
        public void play(SpaceId spaceId, Vec3 position, Sound sound) {
            played.add(spaceId.key().asString() + "@"
                    + position.x() + "," + position.y() + "," + position.z()
                    + "=" + sound.name().asString() + "@" + sound.volume() + "/" + sound.pitch());
        }
    }
}
