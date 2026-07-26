package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;
import sh.harold.library.entity.EntityPose;
import sh.harold.library.entity.EquipmentSlot;
import sh.harold.library.entity.InteractionHand;
import sh.harold.library.entity.ItemDescriptor;
import sh.harold.library.npc.behavior.NpcRoutine;
import sh.harold.library.npc.behavior.NpcGesturePreset;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcStance;
import sh.harold.library.npc.behavior.NpcTimingBand;
import sh.harold.library.spatial.AnchorRef;
import sh.harold.library.spatial.AnchorSnapshot;
import sh.harold.library.spatial.Frame3;
import sh.harold.library.spatial.SpaceId;
import sh.harold.library.spatial.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRoutinePlayerTest {

    private static final SpaceId SPACE = SpaceId.of("test", "world");
    private static final ItemDescriptor BOOK = item("book");
    private static final ItemDescriptor SWORD = item("iron_sword");

    @Test
    void routineUsesDeclarativeStepsAndRestoresNewestUnderlyingBase() {
        RecordingPort port = new RecordingPort();
        AtomicReference<NpcRenderFrame> base = new AtomicReference<>(frame(Map.of()));
        NpcRoutinePlayer player = player(port, base);
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "prop"))
                .equip(EquipmentSlot.MAIN_HAND, BOOK)
                .useItem(InteractionHand.MAIN_HAND, NpcTimingBand.QUICK)
                .wait(NpcTimingBand.QUICK)
                .build();

        NpcRoutinePlayer.Ticket ticket = player.perform(routine);
        player.tick(0);
        assertEquals(BOOK, player.composedFrame().equipment().get(EquipmentSlot.MAIN_HAND));
        assertTrue(player.composedFrame().usingMainHand());

        base.set(frame(Map.of(EquipmentSlot.MAIN_HAND, SWORD)));
        for (int tick = 1; tick <= 8; tick++) {
            player.tick(tick);
        }

        assertTrue(ticket.done());
        assertFalse(player.active());
        assertEquals(SWORD, player.composedFrame().equipment().get(EquipmentSlot.MAIN_HAND));
        assertFalse(player.composedFrame().usingMainHand());
        assertEquals(List.of(NpcRenderAnimation.Type.USE_MAIN_HAND), port.animations.stream()
                .map(NpcRenderAnimation::type)
                .toList());
    }

    @Test
    void cancellationWaitsForCheckpointAndRestartBeginsAtStepZero() {
        RecordingPort port = new RecordingPort();
        AtomicReference<NpcRenderFrame> base = new AtomicReference<>(frame(Map.of()));
        NpcRoutinePlayer player = player(port, base);
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "restart"))
                .equip(EquipmentSlot.MAIN_HAND, BOOK)
                .wait(NpcTimingBand.QUICK)
                .swing(InteractionHand.MAIN_HAND)
                .build();

        NpcRoutinePlayer.Ticket first = player.perform(routine);
        player.tick(0);
        player.cancel(first);
        player.tick(3);
        assertFalse(first.done());
        player.tick(4);
        assertTrue(first.done());
        assertTrue(port.animations.isEmpty(), "post-checkpoint swing was never entered");
        assertFalse(player.composedFrame().equipment().containsKey(EquipmentSlot.MAIN_HAND));

        NpcRoutinePlayer.Ticket second = player.perform(routine);
        player.tick(5);
        assertEquals(BOOK, player.composedFrame().equipment().get(EquipmentSlot.MAIN_HAND));
        player.tick(9);
        assertEquals(List.of(NpcRenderAnimation.Type.SWING_MAIN_HAND), port.animations.stream()
                .map(NpcRenderAnimation::type)
                .toList());
        player.tick(10);
        assertTrue(second.done());
    }

    @Test
    void missingAndWrongSpaceAnchorsRemainTemporarilyIneligible() {
        RecordingPort port = new RecordingPort();
        AtomicReference<NpcRenderFrame> base = new AtomicReference<>(frame(Map.of()));
        NpcRoutinePlayer player = player(port, base);
        AnchorRef.Fixed missing = fixed(SPACE, new Vec3(0.0, 1.0, 2.0));
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "look"))
                .lookAt(missing, NpcTimingBand.QUICK)
                .build();

        NpcRoutinePlayer.Ticket ticket = player.perform(routine);
        player.tick(0);
        assertFalse(player.active());
        assertFalse(ticket.done());

        port.anchor = Optional.of(new AnchorSnapshot(
                SpaceId.of("test", "other"),
                Frame3.world(new Vec3(0.0, 1.0, 2.0))
        ));
        player.tick(1);
        assertFalse(player.active());

        port.anchor = Optional.of(((AnchorRef.Fixed) missing).snapshot());
        player.tick(2);
        assertTrue(player.active());
        player.tick(6);
        assertTrue(ticket.done());
    }

    @Test
    void missingActorSpaceMakesAnchoredRoutineIneligible() {
        RecordingPort port = new RecordingPort();
        AnchorRef.Fixed anchor = fixed(SPACE, new Vec3(0.0, 1.0, 2.0));
        port.anchor = Optional.of(anchor.snapshot());
        NpcRoutinePlayer player = new NpcRoutinePlayer(
                port,
                new MinimumRandom(),
                () -> frame(Map.of()),
                () -> Vec3.ZERO,
                Optional::empty
        );
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "space_required"))
                .lookAt(anchor, NpcTimingBand.QUICK)
                .build();

        NpcRoutinePlayer.Ticket ticket = player.perform(routine);
        player.tick(0);

        assertFalse(player.active());
        assertFalse(ticket.done());
    }

    @Test
    void newerExplicitRoutinePreemptsOlderExplicitAtNextCheckpoint() {
        RecordingPort port = new RecordingPort();
        NpcRoutinePlayer player = player(port, new AtomicReference<>(frame(Map.of())));
        NpcRoutine firstRoutine = NpcRoutine.builder(Key.key("test", "first_explicit"))
                .wait(NpcTimingBand.QUICK)
                .swing(InteractionHand.OFF_HAND)
                .build();
        NpcRoutine secondRoutine = NpcRoutine.builder(Key.key("test", "second_explicit"))
                .swing(InteractionHand.MAIN_HAND)
                .build();

        NpcRoutinePlayer.Ticket first = player.perform(firstRoutine);
        player.tick(0);
        NpcRoutinePlayer.Ticket second = player.perform(secondRoutine);
        player.tick(3);
        assertFalse(first.done());
        player.tick(4);

        assertTrue(first.done());
        assertTrue(second.done());
        assertEquals(List.of(NpcRenderAnimation.Type.SWING_MAIN_HAND), port.animations.stream()
                .map(NpcRenderAnimation::type)
                .toList());
    }

    @Test
    void gestureAndSoundBeatAreEmittedTogether() {
        RecordingPort port = new RecordingPort();
        NpcRoutinePlayer player = player(port, new AtomicReference<>(frame(Map.of())));
        NpcSoundProfile sound = NpcSoundProfile.of(new NpcSoundProfile.Variant(
                Key.key("minecraft", "block.anvil.hit"),
                Sound.Source.BLOCK,
                1.0f,
                1.0f
        ));
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "beat"))
                .swing(InteractionHand.OFF_HAND, sound)
                .wait(NpcTimingBand.QUICK)
                .build();

        player.perform(routine);
        player.tick(0);

        assertEquals(NpcRenderAnimation.Type.SWING_OFF_HAND, port.animations.get(0).type());
        assertEquals(Key.key("minecraft", "block.anvil.hit"), port.sounds.get(0).key());
    }

    @Test
    void everyStatefulPrimitiveSequencesAndRestoresTheNewestCompleteBaseFrame() {
        RecordingPort port = new RecordingPort();
        AnchorRef.Fixed from = fixed(SPACE, new Vec3(0.0, 0.0, 2.0));
        AnchorRef.Fixed to = fixed(SPACE, new Vec3(-2.0, 0.0, 0.0));
        port.anchors.put(from, from.snapshot());
        port.anchors.put(to, to.snapshot());
        NpcRenderFrame initial = new NpcRenderFrame(
                10.0f,
                10.0f,
                5.0f,
                EntityPose.STANDING,
                Map.of(EquipmentSlot.HEAD, SWORD, EquipmentSlot.OFF_HAND, SWORD),
                false,
                false
        );
        AtomicReference<NpcRenderFrame> base = new AtomicReference<>(initial);
        NpcRoutinePlayer player = player(port, base);
        NpcSoundProfile cue = NpcSoundProfile.of(new NpcSoundProfile.Variant(
                Key.key("minecraft", "ui.button.click"),
                Sound.Source.BLOCK,
                0.5f,
                1.0f
        ));
        NpcRoutine routine = NpcRoutine.builder(Key.key("test", "all_stateful_primitives"))
                .stance(NpcStance.CROUCHING)
                .clear(EquipmentSlot.HEAD)
                .equipOneOf(EquipmentSlot.MAIN_HAND, List.of(BOOK, SWORD))
                .sound(cue)
                .sweep(from, to, NpcTimingBand.QUICK)
                .stance(NpcStance.STANDING)
                .clear(EquipmentSlot.MAIN_HAND)
                .gesture(NpcGesturePreset.NOD, cue)
                .wait(NpcTimingBand.QUICK)
                .build();

        NpcRoutinePlayer.Ticket ticket = player.perform(routine);
        player.tick(0);
        NpcRenderFrame started = player.composedFrame();
        assertEquals(EntityPose.CROUCHING, started.pose());
        assertFalse(started.equipment().containsKey(EquipmentSlot.HEAD));
        assertEquals(BOOK, started.equipment().get(EquipmentSlot.MAIN_HAND));
        assertEquals(0.0f, started.bodyYaw(), 0.001f);
        assertEquals(1, port.sounds.size());

        player.tick(2);
        assertEquals(45.0f, player.composedFrame().bodyYaw(), 0.001f,
                "the sweep interpolates between both authored anchors");

        player.tick(4);
        assertEquals(EntityPose.STANDING, player.composedFrame().pose());
        assertFalse(player.composedFrame().equipment().containsKey(EquipmentSlot.MAIN_HAND));
        assertEquals(NpcRenderAnimation.Type.NOD, port.animations.get(0).type());
        assertEquals(2, port.sounds.size(), "the explicit cue and gesture cue each play once");
        assertFalse(player.atCleanupCheckpoint(), "the visible gesture is an atomic timed primitive");

        NpcRenderFrame newestBase = new NpcRenderFrame(
                -30.0f,
                -25.0f,
                3.0f,
                EntityPose.CROUCHING,
                Map.of(EquipmentSlot.MAIN_HAND, SWORD, EquipmentSlot.HEAD, BOOK),
                false,
                true
        );
        base.set(newestBase);
        player.tick(5);
        assertFalse(ticket.done());

        player.tick(10);
        assertFalse(ticket.done(), "the final authored wait still owns the routine");
        player.tick(14);

        assertTrue(ticket.done());
        assertFalse(player.active());
        assertEquals(newestBase, player.composedFrame(),
                "cleanup restores current equipment, pose, active hand, and orientation together");
    }

    private static NpcRoutinePlayer player(RecordingPort port, AtomicReference<NpcRenderFrame> base) {
        return new NpcRoutinePlayer(
                port,
                new MinimumRandom(),
                base::get,
                () -> Vec3.ZERO,
                () -> Optional.of(SPACE)
        );
    }

    private static NpcRenderFrame frame(Map<EquipmentSlot, ItemDescriptor> items) {
        return new NpcRenderFrame(0.0f, 0.0f, 0.0f, EntityPose.STANDING, items, false, false);
    }

    private static ItemDescriptor item(String value) {
        return new ItemDescriptor(Key.key("minecraft", value), 1);
    }

    private static AnchorRef.Fixed fixed(SpaceId space, Vec3 point) {
        return new AnchorRef.Fixed(new AnchorSnapshot(space, Frame3.world(point)));
    }

    private static final class RecordingPort implements NpcBehaviorRenderPort {
        private final List<NpcRenderAnimation> animations = new ArrayList<>();
        private final List<NpcRenderedSound> sounds = new ArrayList<>();
        private final Map<AnchorRef, AnchorSnapshot> anchors = new java.util.HashMap<>();
        private Optional<AnchorSnapshot> anchor = Optional.empty();

        @Override
        public Optional<AnchorSnapshot> resolveAnchor(AnchorRef requested) {
            return Optional.ofNullable(anchors.get(requested)).or(() -> anchor);
        }

        @Override
        public void animateShared(NpcRenderAnimation animation) {
            animations.add(animation);
        }

        @Override
        public void playSound(NpcRenderedSound sound) {
            sounds.add(sound);
        }
    }

    private static final class MinimumRandom implements NpcBehaviorRandom {
        @Override
        public int nextInt(int originInclusive, int boundExclusive) {
            return originInclusive;
        }

        @Override
        public double nextDouble() {
            return 0.0;
        }
    }
}
