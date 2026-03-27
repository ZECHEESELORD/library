package sh.harold.creative.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.message.Click;
import sh.harold.creative.library.message.Message;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.NoticeMessage;
import sh.harold.creative.library.message.TopicMessage;
import sh.harold.creative.library.message.Topics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageGoldenOutputTest {

    @Test
    void noticeChatOutputMatchesGoldenSnapshot() {
        NoticeMessage message = Message.info(
                "Set rank of {player} to {rank}.",
                Message.slot("player", "ZeCheeseLord"),
                Message.slot("rank", Message.value("ADMIN").color(0x55FF55))
        ).tag(sh.harold.creative.library.message.Tag.STAFF);

        assertSnapshot(
                """
                TEXT:
                [STAFF] Set rank of ZeCheeseLord to ADMIN.
                TREE:
                text("")
                  text("[STAFF]") color=aqua
                  text(" ")
                  text("Set rank of ") color=gray
                  text("ZeCheeseLord") color=aqua
                  text(" to ") color=gray
                  text("ADMIN") color=green
                  text(".") color=gray
                """,
                renderChat(message)
        );
    }

    @Test
    void topicChatOutputMatchesGoldenSnapshot() {
        TopicMessage message = Message.topic(
                Topics.SOUL,
                "You found {count}x fairy soul!",
                Message.slot("count", 1)
        );

        assertSnapshot(
                """
                TEXT:
                SOUL! You found 1x fairy soul!
                TREE:
                text("")
                  text("SOUL!") color=light_purple bold
                  text(" ")
                  text("You found ") color=gray
                  text("1") color=yellow
                  text("x fairy soul!") color=gray
                """,
                renderChat(message)
        );
    }

    @Test
    void blockChatOutputMatchesGoldenSnapshot() {
        MessageBlock block = Message.block()
                .title("+ AREA DISCOVERED", NamedTextColor.GOLD)
                .line(
                        "You discovered {area}!",
                        Message.slot("area", Message.value("The Barn").color(0x55FF55))
                )
                .blank()
                .bullets("Fast travel unlocked", "New NPCs available")
                .build();

        assertSnapshot(
                """
                TEXT:

                + AREA DISCOVERED
                 You discovered The Barn!
                
                 • Fast travel unlocked
                 • New NPCs available
                
                TREE:
                text("")
                  text("\\n")
                  text("+ AREA DISCOVERED") color=gold bold
                  text("\\n")
                  text(" ") color=gray
                  text("You discovered ") color=gray
                  text("The Barn") color=green
                  text("!") color=gray
                  text("\\n")
                  text("\\n")
                  text(" ") color=gray
                  text("• ") color=dark_gray
                  text("Fast travel unlocked") color=gray
                  text("\\n")
                  text(" ") color=gray
                  text("• ") color=dark_gray
                  text("New NPCs available") color=gray
                  text("\\n")
                """,
                renderBlock(block)
        );
    }

    @Test
    void bakedInColorPreservationMatchesGoldenSnapshot() {
        NoticeMessage message = Message.info(
                "You are holding {item}.",
                Message.slot("item", Component.text("Aspect of the Dragons", NamedTextColor.GOLD))
        );

        assertSnapshot(
                """
                TEXT:
                You are holding Aspect of the Dragons.
                TREE:
                text("")
                  text("You are holding ") color=gray
                  text("Aspect of the Dragons") color=gold
                  text(".") color=gray
                """,
                renderChat(message)
        );
    }

    @Test
    void multiClickLineMatchesGoldenSnapshot() {
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

        assertSnapshot(
                """
                TEXT:

                 CLICK for Wiki, CLICK for Discord

                TREE:
                text("")
                  text("\\n")
                  text(" ") color=gray
                  text("CLICK") color=yellow bold click=open_url(https://example.com/wiki)
                  text(" for ") color=gray
                  text("Wiki") color=green click=open_url(https://example.com/wiki)
                  text(", ") color=gray
                  text("CLICK") color=yellow bold click=open_url(https://example.com/discord)
                  text(" for ") color=gray
                  text("Discord") color=#5865F2 click=open_url(https://example.com/discord)
                  text("\\n")
                """,
                renderBlock(block)
        );
    }

    @Test
    void hoverOutputMatchesGoldenSnapshot() {
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

        assertSnapshot(
                """
                TEXT:
                Use Wiki for more info.
                TREE:
                text("")
                  hover {
                    text("")
                      text("+ HELP") color=gold bold
                      text("\\n")
                      text("")
                        text("Click the wiki link for details.") color=gray
                  }
                  text("Use ") color=gray
                  text("Wiki") color=green click=open_url(https://example.com/wiki)
                    hover {
                      text("")
                        text("Open the wiki") color=gray
                    }
                  text(" for more info.") color=gray
                """,
                renderChat(message)
        );
    }

    @Test
    void actionBarOutputMatchesGoldenSnapshot() {
        NoticeMessage message = Message.success(
                "Paid {amount} to {player}.",
                Message.slot("amount", Message.value("500 Coins").color(NamedTextColor.GOLD)),
                Message.slot("player", "Notch")
        );

        assertSnapshot(
                """
                TEXT:
                Paid 500 Coins to Notch.
                TREE:
                text("")
                  text("Paid ") color=green
                  text("500 Coins") color=gold
                  text(" to ") color=green
                  text("Notch") color=yellow
                  text(".") color=green
                """,
                renderActionBar(message)
        );
    }

    @Test
    void validationFailuresUseExactMessages() {
        IllegalArgumentException unmatchedOpening = assertThrows(IllegalArgumentException.class, () -> Message.info("Hello {player"));
        IllegalArgumentException missingSlot = assertThrows(IllegalArgumentException.class, () -> Message.info("Hello {player}"));
        IllegalArgumentException unknownSlot = assertThrows(IllegalArgumentException.class, () -> Message.info(
                "Hello {player}",
                Message.slot("player", "Alpha"),
                Message.slot("rank", "ADMIN")
        ));
        IllegalArgumentException missingClick = assertThrows(IllegalArgumentException.class, () -> Message.block()
                .line("{click:wiki}", Message.slot("wiki", "Wiki")));

        assertEquals("template contains an unmatched opening brace: Hello {player", unmatchedOpening.getMessage());
        assertEquals("missing slot bindings [player] for template: Hello {player}", missingSlot.getMessage());
        assertEquals("unknown slot bindings [rank] for template: Hello {player}", unknownSlot.getMessage());
        assertEquals("{click:wiki} requires a click-enabled slot", missingClick.getMessage());
    }

    private static void assertSnapshot(String expected, Component component) {
        assertEquals(normalize(expected), ComponentSnapshot.snapshot(component));
    }

    private static String normalize(String value) {
        return value.stripIndent().stripLeading().stripTrailing();
    }

    private static Component renderChat(NoticeMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.CHAT);
    }

    private static Component renderChat(TopicMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.CHAT);
    }

    private static Component renderActionBar(NoticeMessage message) {
        return DefaultMessageRenderer.INSTANCE.renderInline((CompiledInlineMessage) message, RenderTarget.ACTION_BAR);
    }

    private static Component renderBlock(MessageBlock block) {
        return DefaultMessageRenderer.INSTANCE.renderBlock(block, RenderTarget.CHAT);
    }
}
