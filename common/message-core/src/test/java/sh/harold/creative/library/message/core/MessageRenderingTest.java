package sh.harold.creative.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.message.Click;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.NoticeMessage;
import sh.harold.creative.library.message.Tag;
import sh.harold.creative.library.message.TopicMessage;
import sh.harold.creative.library.message.Topics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRenderingTest {

    @Test
    void infoNoticeRendersTagAndNamedBindings() {
        NoticeMessage message = Message.info(
                "Set rank of {player} to {rank}.",
                Message.slot("player", "ZeCheeseLord"),
                Message.slot("rank", Message.value("ADMIN").color(0x55FF55))
        ).tag(Tag.STAFF);

        Component expected = Component.text()
                .append(Component.text("[STAFF]", NamedTextColor.AQUA))
                .append(Component.space())
                .append(Component.text("Set rank of ", NamedTextColor.GRAY))
                .append(Component.text("ZeCheeseLord", NamedTextColor.AQUA))
                .append(Component.text(" to ", NamedTextColor.GRAY))
                .append(Component.text("ADMIN", TextColor.color(0x55FF55)))
                .append(Component.text(".", NamedTextColor.GRAY))
                .build();

        assertEquals(expected, renderChat(message));
    }

    @Test
    void styledComponentValueKeepsItsOwnColor() {
        NoticeMessage message = Message.info(
                "You are holding {item}.",
                Message.slot("item", Component.text("Aspect of the Dragons", NamedTextColor.GOLD))
        );

        Component expected = Component.text()
                .append(Component.text("You are holding ", NamedTextColor.GRAY))
                .append(Component.text("Aspect of the Dragons", NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GRAY))
                .build();

        assertEquals(expected, renderChat(message));
    }

    @Test
    void topicMessageRendersBoldTopicLabel() {
        TopicMessage message = Message.topic(
                Topics.SOUL,
                "You found {count}x fairy soul!",
                Message.slot("count", 1)
        );

        Component expected = Component.text()
                .append(Component.text("SOUL!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text("You found ", NamedTextColor.GRAY))
                .append(Component.text("1", NamedTextColor.YELLOW))
                .append(Component.text("x fairy soul!", NamedTextColor.GRAY))
                .build();

        assertEquals(expected, renderChat(message));
    }

    @Test
    void blockRendersTitlesBlankLinesAndBullets() {
        MessageBlock block = Message.block()
                .title("+ AREA DISCOVERED", NamedTextColor.GOLD)
                .line(
                        "You discovered {area}!",
                        Message.slot("area", Message.value("The Barn").color(0x55FF55))
                )
                .blank()
                .bullet("Fast travel unlocked")
                .bullet("New NPCs available")
                .build();

        Component expected = Component.join(
                JoinConfiguration.separator(Component.newline()),
                java.util.List.of(
                        Component.empty(),
                        Component.text("+ AREA DISCOVERED", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text()
                                .append(Component.text(" ", NamedTextColor.GRAY))
                                .append(Component.text("You discovered ", NamedTextColor.GRAY))
                                .append(Component.text("The Barn", TextColor.color(0x55FF55)))
                                .append(Component.text("!", NamedTextColor.GRAY))
                                .build(),
                        Component.empty(),
                        Component.text()
                                .append(Component.text(" ", NamedTextColor.GRAY))
                                .append(Component.text("\u2022 ", NamedTextColor.DARK_GRAY))
                                .append(Component.text("Fast travel unlocked", NamedTextColor.GRAY))
                                .build(),
                        Component.text()
                                .append(Component.text(" ", NamedTextColor.GRAY))
                                .append(Component.text("\u2022 ", NamedTextColor.DARK_GRAY))
                                .append(Component.text("New NPCs available", NamedTextColor.GRAY))
                                .build(),
                        Component.empty()
                )
        );

        assertEquals(expected, renderBlock(block));
    }

    @Test
    void clickPromptsRenderMultipleIndependentActions() {
        MessageBlock block = Message.block()
                .line(
                        "{click:wiki} for {wiki}, {click:discord} for {discord}",
                        Message.slot("wiki", Message.value("Wiki")
                                .color(0x55FF55)
                                .click(Click.openUrl("https://example.com/wiki"))),
                        Message.slot("discord", Message.value("Discord")
                                .color(0x5865F2)
                                .click(Click.openUrl("https://example.com/discord")))
                )
                .build();

        Component expected = Component.join(
                JoinConfiguration.separator(Component.newline()),
                java.util.List.of(
                        Component.empty(),
                        Component.text()
                                .append(Component.text(" ", NamedTextColor.GRAY))
                                .append(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD)
                                        .clickEvent(ClickEvent.openUrl("https://example.com/wiki")))
                                .append(Component.text(" for ", NamedTextColor.GRAY))
                                .append(Component.text("Wiki", TextColor.color(0x55FF55))
                                        .clickEvent(ClickEvent.openUrl("https://example.com/wiki")))
                                .append(Component.text(", ", NamedTextColor.GRAY))
                                .append(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD)
                                        .clickEvent(ClickEvent.openUrl("https://example.com/discord")))
                                .append(Component.text(" for ", NamedTextColor.GRAY))
                                .append(Component.text("Discord", TextColor.color(0x5865F2))
                                        .clickEvent(ClickEvent.openUrl("https://example.com/discord")))
                                .build(),
                        Component.empty()
                )
        );

        assertEquals(expected, renderBlock(block));
    }

    @Test
    void wholeMessageHoverAndValueHoverCoexist() {
        MessageBlock valueHover = Message.block()
                .line("Open the wiki")
                .build();
        MessageBlock messageHover = Message.block()
                .title("+ HELP", NamedTextColor.GOLD)
                .line("Click the wiki link for details.")
                .build();

        NoticeMessage message = Message.info(
                "Use {wiki} for more info.",
                Message.slot("wiki", Message.value("Wiki")
                        .color(0x55FF55)
                        .click(Click.openUrl("https://example.com/wiki"))
                        .hover(valueHover))
        ).hover(messageHover);

        Component expected = Component.text()
                .append(Component.text("Use ", NamedTextColor.GRAY))
                .append(Component.text("Wiki", TextColor.color(0x55FF55))
                        .clickEvent(ClickEvent.openUrl("https://example.com/wiki"))
                        .hoverEvent(HoverEvent.showText(renderHover(valueHover))))
                .append(Component.text(" for more info.", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(renderHover(messageHover)))
                .build();

        assertEquals(expected, renderChat(message));
    }

    @Test
    void hoverRenderingStripsInteractivityFromBlockContent() {
        MessageBlock block = Message.block()
                .line(
                        "{click:wiki} to open {wiki}",
                        Message.slot("wiki", Message.value("Wiki")
                                .color(0x55FF55)
                                .click(Click.openUrl("https://example.com/wiki"))
                                .hover(Message.block().line("Nested hover").build()))
                )
                .build();

        Component expected = Component.text()
                .append(Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" to open ", NamedTextColor.GRAY))
                .append(Component.text("Wiki", TextColor.color(0x55FF55)))
                .build();

        assertEquals(expected, renderHover(block));
    }

    @Test
    void invalidTemplatesAndBindingsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> Message.info("Hello {player"));
        assertThrows(IllegalArgumentException.class, () -> Message.info("Hello {player}"));
        assertThrows(IllegalArgumentException.class, () -> Message.info(
                "Hello {player}",
                Message.slot("player", "Alpha"),
                Message.slot("rank", "ADMIN")
        ));
        assertThrows(IllegalArgumentException.class, () -> Message.block()
                .line("{click:wiki}", Message.slot("wiki", "Wiki")));
    }

    @Test
    void withCreatesIndependentImmutableMessages() {
        NoticeMessage original = Message.success(
                "Paid {amount} to {player}.",
                Message.slot("amount", Message.value("500 Coins").color(NamedTextColor.GOLD)),
                Message.slot("player", "Notch")
        );
        NoticeMessage updated = original.with("player", "Steve");

        Component originalExpected = Component.text()
                .append(Component.text("Paid ", NamedTextColor.GREEN))
                .append(Component.text("500 Coins", NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("Notch", NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN))
                .build();
        Component updatedExpected = Component.text()
                .append(Component.text("Paid ", NamedTextColor.GREEN))
                .append(Component.text("500 Coins", NamedTextColor.GOLD))
                .append(Component.text(" to ", NamedTextColor.GREEN))
                .append(Component.text("Steve", NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN))
                .build();

        assertEquals(originalExpected, renderChat(original));
        assertEquals(updatedExpected, renderChat(updated));
        assertEquals(Component.text("Notch"), original.bindings().get("player").component());
        assertTrue(updated.bindings().containsKey("player"));
    }

    private static Component renderChat(NoticeMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.CHAT);
    }

    private static Component renderChat(TopicMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.CHAT);
    }

    private static Component renderBlock(MessageBlock block) {
        return DefaultMessageRenderer.INSTANCE.renderBlock(block, RenderTarget.CHAT);
    }

    private static Component renderHover(MessageBlock block) {
        return DefaultMessageRenderer.INSTANCE.renderBlock(block, RenderTarget.HOVER);
    }
}
