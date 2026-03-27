package sh.harold.creative.library.message.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageBlockBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageBlockBuilderTest {

    @Test
    void bulletsVarargsAppendOrdinaryBulletEntriesInOrder() {
        MessageBlock block = Message.block()
                .bullets("Fast travel unlocked", "New NPCs available")
                .build();

        assertEquals(2, block.entries().size());
        MessageBlock.BulletEntry first = assertInstanceOf(MessageBlock.BulletEntry.class, block.entries().get(0));
        MessageBlock.BulletEntry second = assertInstanceOf(MessageBlock.BulletEntry.class, block.entries().get(1));
        assertEquals("Fast travel unlocked", first.template());
        assertEquals("New NPCs available", second.template());
    }

    @Test
    void bulletsIterableAndFormatterDelegateToBulletPrimitive() {
        MessageBlock iterableBlock = Message.block()
                .bullets(List.of("Alpha", "Beta"))
                .build();
        MessageBlock formattedBlock = Message.block()
                .bullets(List.of(1, 2), count -> count + "x reward")
                .build();

        assertEquals(2, iterableBlock.entries().size());
        assertEquals("Alpha", assertInstanceOf(MessageBlock.BulletEntry.class, iterableBlock.entries().get(0)).template());
        assertEquals("Beta", assertInstanceOf(MessageBlock.BulletEntry.class, iterableBlock.entries().get(1)).template());

        assertEquals(2, formattedBlock.entries().size());
        assertEquals("1x reward", assertInstanceOf(MessageBlock.BulletEntry.class, formattedBlock.entries().get(0)).template());
        assertEquals("2x reward", assertInstanceOf(MessageBlock.BulletEntry.class, formattedBlock.entries().get(1)).template());
    }

    @Test
    void bulletsMapperCanEmitMultipleBulletsPerItem() {
        MessageBlock block = Message.block()
                .bullets(List.of("Coins", "XP"), (builder, reward) -> builder
                        .bullet("Earn " + reward)
                        .bullet("Claim " + reward))
                .build();

        assertEquals(4, block.entries().size());
        assertEquals("Earn Coins", assertInstanceOf(MessageBlock.BulletEntry.class, block.entries().get(0)).template());
        assertEquals("Claim Coins", assertInstanceOf(MessageBlock.BulletEntry.class, block.entries().get(1)).template());
        assertEquals("Earn XP", assertInstanceOf(MessageBlock.BulletEntry.class, block.entries().get(2)).template());
        assertEquals("Claim XP", assertInstanceOf(MessageBlock.BulletEntry.class, block.entries().get(3)).template());
    }

    @Test
    void bulletsMapperRejectsNonBulletEntriesAndWrongBuilderReturns() {
        IllegalStateException titleFailure = assertThrows(IllegalStateException.class, () -> Message.block()
                .bullets(List.of("Alpha"), (builder, item) -> builder.title(item, 0x55FF55)));
        IllegalStateException blankFailure = assertThrows(IllegalStateException.class, () -> Message.block()
                .bullets(List.of("Alpha"), (builder, item) -> builder.blank()));
        IllegalStateException lineFailure = assertThrows(IllegalStateException.class, () -> Message.block()
                .bullets(List.of("Alpha"), (builder, item) -> builder.line(item)));
        IllegalArgumentException wrongBuilderFailure = assertThrows(IllegalArgumentException.class, () -> Message.block()
                .bullets(List.of("Alpha"), (builder, item) -> Message.block()));

        assertEquals("bullets(...) mapper may only add bullet entries", titleFailure.getMessage());
        assertEquals("bullets(...) mapper may only add bullet entries", blankFailure.getMessage());
        assertEquals("bullets(...) mapper may only add bullet entries", lineFailure.getMessage());
        assertEquals("bullets(...) mapper must return the provided builder", wrongBuilderFailure.getMessage());
    }

    @Test
    void bulletsHelpersRejectNullInputsAndNullMapperResults() {
        MessageBlockBuilder builder = Message.block();

        NullPointerException nullVarargs = assertThrows(NullPointerException.class, () -> builder.bullets((String[]) null));
        NullPointerException nullIterable = assertThrows(NullPointerException.class, () -> builder.bullets((Iterable<String>) null));
        NullPointerException nullFormatter = assertThrows(
                NullPointerException.class,
                () -> builder.bullets(List.of(1), (java.util.function.Function<Integer, String>) null)
        );
        NullPointerException nullMapper = assertThrows(NullPointerException.class, () -> builder.bullets(List.of(1), (java.util.function.BiFunction<MessageBlockBuilder, Integer, MessageBlockBuilder>) null));
        NullPointerException nullMapperResult = assertThrows(NullPointerException.class, () -> builder.bullets(List.of("Alpha"), (mappedBuilder, item) -> null));

        assertEquals("lines", nullVarargs.getMessage());
        assertEquals("lines", nullIterable.getMessage());
        assertEquals("formatter", nullFormatter.getMessage());
        assertEquals("mapper", nullMapper.getMessage());
        assertEquals("mapper result", nullMapperResult.getMessage());
    }
}
