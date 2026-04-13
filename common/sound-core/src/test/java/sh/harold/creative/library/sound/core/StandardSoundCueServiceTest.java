package sh.harold.creative.library.sound.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.sound.CuePlayback;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundTarget;
import sh.harold.creative.library.spatial.SpaceId;
import sh.harold.creative.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sh.harold.creative.library.sound.SoundCues.atTick;
import static sh.harold.creative.library.sound.SoundCues.layer;
import static sh.harold.creative.library.sound.SoundCues.oneOf;
import static sh.harold.creative.library.sound.SoundCues.sequence;
import static sh.harold.creative.library.sound.SoundCues.silent;
import static sh.harold.creative.library.sound.SoundCues.sound;

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
