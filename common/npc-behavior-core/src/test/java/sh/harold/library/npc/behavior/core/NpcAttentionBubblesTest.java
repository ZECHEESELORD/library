package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import sh.harold.library.npc.behavior.NpcSoundProfile;
import sh.harold.library.npc.behavior.NpcVoiceDeliveryStyle;
import sh.harold.library.npc.behavior.NpcVoiceProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAttentionBubblesTest {

    private static final UUID P1 = uuid(1);
    private static final UUID P2 = uuid(2);
    private static final UUID P3 = uuid(3);
    private static final UUID OBSERVER = uuid(9);

    @Test
    void supersededTargetsFinishOwnBubbleAndNeverReceiveLaterTextOrSound() {
        RecordingPort port = new RecordingPort();
        NpcAttentionBubbles bubbles = new NpcAttentionBubbles(port, new MinimumRandom());
        Set<UUID> tracked = Set.of(P1, P2, P3, OBSERVER);

        NpcBubbleFrame first = bubbles.show(P1, Component.text("one"), voice(), tracked, Set.of(P1), 0);
        NpcBubbleFrame second = bubbles.show(P2, Component.text("two"), voice(), tracked, Set.of(P1, P2), 1);
        NpcBubbleFrame third = bubbles.show(P3, Component.text("three"), voice(), tracked, Set.of(P1, P2, P3), 2);

        assertEquals(Set.of(P1, P2), third.excludedViewers());
        assertEquals("one", port.virtual.get(P1).text().toString().contains("one") ? "one" : "missing");
        assertEquals(second.id(), port.virtual.get(P2).id());
        assertEquals(Set.of(P1, P2), bubbles.snapshot().virtualBubbles().keySet());

        Map<UUID, Integer> soundCounts = new LinkedHashMap<>();
        port.sounds.forEach(sound -> sound.recipient().ifPresent(viewer -> soundCounts.merge(viewer, 1, Integer::sum)));
        assertEquals(1, soundCounts.get(P1));
        assertEquals(2, soundCounts.get(P2));
        assertEquals(3, soundCounts.get(P3));
        assertEquals(3, soundCounts.get(OBSERVER));
        assertTrue(port.clearedShared.contains(first.id()));
        assertTrue(port.clearedShared.contains(second.id()));
    }

    @Test
    void releaseAndExpiryDestroyVirtualCopiesExactlyOnce() {
        RecordingPort port = new RecordingPort();
        NpcAttentionBubbles bubbles = new NpcAttentionBubbles(port, new MinimumRandom());
        bubbles.show(P1, Component.text("one"), voice(), Set.of(P1, P2), Set.of(P1), 0);
        NpcBubbleFrame real = bubbles.show(P2, Component.text("two"), voice(), Set.of(P1, P2), Set.of(P1, P2), 1);

        bubbles.release(P1);
        bubbles.release(P1);
        assertEquals(List.of(P1), port.clearedVirtual);
        assertFalse(bubbles.snapshot().realBubble().orElseThrow().excludedViewers().contains(P1));

        bubbles.tick(real.expiresAtTick());
        assertTrue(bubbles.snapshot().realBubble().isEmpty());
        assertTrue(port.clearedShared.contains(real.id()));
    }

    private static NpcVoiceProfile voice() {
        return new NpcVoiceProfile(
                NpcSoundProfile.of(new NpcSoundProfile.Variant(
                        Key.key("minecraft", "entity.villager.ambient"),
                        Sound.Source.NEUTRAL,
                        1.0f,
                        1.0f
                )),
                NpcVoiceDeliveryStyle.NEUTRAL
        );
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }

    private static final class RecordingPort implements NpcBehaviorRenderPort {
        private final Map<UUID, NpcBubbleFrame> virtual = new LinkedHashMap<>();
        private final List<UUID> clearedVirtual = new ArrayList<>();
        private final List<Long> clearedShared = new ArrayList<>();
        private final List<NpcRenderedSound> sounds = new ArrayList<>();

        @Override
        public void showVirtualBubble(UUID viewerId, NpcBubbleFrame bubble) {
            virtual.put(viewerId, bubble);
        }

        @Override
        public void clearVirtualBubble(UUID viewerId, long bubbleId) {
            virtual.remove(viewerId);
            clearedVirtual.add(viewerId);
        }

        @Override
        public void clearSharedBubble(long bubbleId) {
            clearedShared.add(bubbleId);
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
