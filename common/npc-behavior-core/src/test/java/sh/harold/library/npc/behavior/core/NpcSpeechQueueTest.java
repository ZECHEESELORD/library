package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcSpeechQueueTest {

    @Test
    void fifoShowsWholeBubbleThenBreathBeforeNextLine() {
        List<NpcBubbleFrame> shown = new ArrayList<>();
        List<Long> cleared = new ArrayList<>();
        NpcSpeechQueue queue = new NpcSpeechQueue(shown::add, cleared::add);
        NpcSpeechQueue.Ticket first = queue.append(Component.text("first"), NpcBubbleFrame.Kind.WORLD);
        NpcSpeechQueue.Ticket second = queue.append(Component.text("second"), NpcBubbleFrame.Kind.WORLD);

        queue.tick(0);
        assertEquals("first", plain(shown.get(0).text()));
        queue.tick(39);
        assertTrue(cleared.isEmpty());
        queue.tick(40);
        assertEquals(List.of(0L), cleared);
        assertFalse(first.done(), "the empty breath belongs to the playback");
        assertTrue(queue.snapshot().visibleText().isEmpty(), "the breath has no visible bubble text");
        queue.tick(48);

        assertTrue(first.done());
        assertEquals("second", plain(shown.get(1).text()));
        assertFalse(second.done());
    }

    @Test
    void speakNowClearsVisibleAndEveryPendingWorldbuildingLine() {
        List<NpcBubbleFrame> shown = new ArrayList<>();
        List<Long> cleared = new ArrayList<>();
        NpcSpeechQueue queue = new NpcSpeechQueue(shown::add, cleared::add);
        NpcSpeechQueue.Ticket oldVisible = queue.append(Component.text("old"), NpcBubbleFrame.Kind.WORLD);
        NpcSpeechQueue.Ticket oldPending = queue.append(Component.text("pending"), NpcBubbleFrame.Kind.WORLD);
        queue.tick(0);

        NpcSpeechQueue.Ticket immediate = queue.now(Component.text("now"), NpcBubbleFrame.Kind.WORLD, 1);

        assertTrue(oldVisible.done());
        assertTrue(oldPending.done());
        assertEquals(List.of(0L), cleared);
        assertEquals("now", plain(shown.get(1).text()));
        assertFalse(immediate.done());
        assertEquals(0, queue.snapshot().pending().size());
    }

    @Test
    void interruptionBarrierRetainsOnlyNewestUrgentRequest() {
        List<NpcBubbleFrame> shown = new ArrayList<>();
        NpcSpeechQueue queue = new NpcSpeechQueue(shown::add, ignored -> { });
        NpcSpeechQueue.Barrier barrier = queue.beginInterruptionBarrier();
        NpcSpeechQueue.Ticket first = queue.now(Component.text("first"), NpcBubbleFrame.Kind.WORLD, 0);
        NpcSpeechQueue.Ticket second = queue.now(Component.text("second"), NpcBubbleFrame.Kind.WORLD, 1);

        assertTrue(first.done());
        assertTrue(shown.isEmpty());
        barrier.close();
        queue.tick(2);

        assertEquals("second", plain(shown.get(0).text()));
        assertFalse(second.done());
    }

    @Test
    void richWrappingPreservesStylesAndUsesUnlimitedLines() {
        Component original = Component.text("0123456789012345678901234567890123456789", NamedTextColor.GOLD)
                .append(Component.text("abcdefghij", NamedTextColor.AQUA));
        Component wrapped = NpcSpeechText.wrap(original, 10);

        assertEquals("0123456789\n0123456789\n0123456789\n0123456789\nabcdefghij", plain(wrapped));
        assertEquals(NamedTextColor.GOLD, wrapped.color());
        assertEquals(NamedTextColor.AQUA, wrapped.children().get(0).color());
    }

    @Test
    void graphemeTimingTreatsJoinedEmojiAsOneAndHonorsMinAndCap() {
        assertEquals(1, NpcSpeechText.graphemeCount(Component.text("👨‍👩‍👧‍👦")));
        assertEquals(40, NpcSpeechText.holdTicks(Component.text("short")));
        assertEquals(50, NpcSpeechText.holdTicks(Component.text("x".repeat(30))));
        assertEquals(200, NpcSpeechText.holdTicks(Component.text("x".repeat(1_000))));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
