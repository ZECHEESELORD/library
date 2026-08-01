package sh.harold.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import sh.harold.library.message.Click;
import sh.harold.library.message.Message;
import sh.harold.library.message.MessageBlock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenteredMessageRenderingTest {

    @Test
    void centeredBlockPadsChatLinesWithoutCapturingInteractions() {
        MessageBlock hover = Message.block().line("A rare crafting material.").build();
        MessageBlock block = Message.centeredBlock()
                .title("TREE GIFT", NamedTextColor.DARK_GREEN)
                .line(
                        "Claim {reward}",
                        Message.slot("reward", Message.value("Phanflare")
                                .color(NamedTextColor.LIGHT_PURPLE)
                                .click(Click.runCommand("/claim tree-gift"))
                                .hover(hover))
                )
                .blank()
                .line("Rewards are waiting!")
                .build();

        Component rendered = renderChat(block);
        String[] lines = plain(rendered).split("\n", -1);

        assertTrue(block.centered());
        assertEquals(6, lines.length);
        assertEquals("", lines[0]);
        assertEquals("TREE GIFT", lines[1].stripLeading());
        assertEquals("Claim Phanflare", lines[2].stripLeading());
        assertEquals("", lines[3]);
        assertEquals("Rewards are waiting!", lines[4].stripLeading());
        assertEquals("", lines[5]);
        assertBalanced(lines[1], Component.text("TREE GIFT", NamedTextColor.DARK_GREEN)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        assertBalanced(lines[2], Component.text()
                .append(Component.text("Claim ", NamedTextColor.GRAY))
                .append(Component.text("Phanflare", NamedTextColor.LIGHT_PURPLE))
                .build());

        TextComponent padding = textNodes(rendered).stream()
                .filter(node -> node.content().length() > 1 && node.content().isBlank())
                .findFirst()
                .orElseThrow();
        assertNull(padding.clickEvent());
        assertNull(padding.hoverEvent());

        TextComponent reward = textNodes(rendered).stream()
                .filter(node -> node.content().equals("Phanflare"))
                .findFirst()
                .orElseThrow();
        assertEquals(ClickEvent.runCommand("/claim tree-gift"), reward.clickEvent());
        assertNotNull(reward.hoverEvent());
    }

    @Test
    void centeredBlockDoesNotPadHoverOrOverlongLines() {
        String divider = "\u25ac".repeat(64);
        MessageBlock block = Message.centeredBlock()
                .title(divider, NamedTextColor.DARK_GREEN)
                .line("Centered body")
                .build();

        String[] chatLines = plain(renderChat(block)).split("\n", -1);

        assertEquals(divider, chatLines[1]);
        assertTrue(chatLines[2].startsWith(" "));
        assertEquals(divider + "\nCentered body", plain(renderHover(block)));
        assertFalse(plain(renderHover(block)).startsWith(" "));
    }

    @Test
    void fontMetricsMatchVanillaBitmapAdvancesAndBoldOffsets() {
        assertEquals(4, ChatFontMetrics.width(Component.text(" ")));
        assertEquals(2, ChatFontMetrics.width(Component.text("!")));
        assertEquals(3, ChatFontMetrics.width(Component.text("l")));
        assertEquals(4, ChatFontMetrics.width(Component.text("\"")));
        assertEquals(6, ChatFontMetrics.width(Component.text("A")));
        assertEquals(3, ChatFontMetrics.width(Component.text("\u2022")));
        assertEquals(7, ChatFontMetrics.width(Component.text("@")));
        assertEquals(15, ChatFontMetrics.width(Component.text("A i").decorate(
                net.kyori.adventure.text.format.TextDecoration.BOLD)));
    }

    @Test
    void centeredBulletsMeasureThePrefixAndStyledValueTogether() {
        MessageBlock block = Message.centeredBlock()
                .bullet(
                        "Reward: {reward}",
                        Message.slot("reward", Message.value("500 Coins").color(NamedTextColor.GOLD))
                )
                .build();

        String bullet = plain(renderChat(block)).split("\n", -1)[1];

        assertEquals("\u2022 Reward: 500 Coins", bullet.stripLeading());
        assertBalanced(bullet, Component.text()
                .append(Component.text("\u2022 ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Reward: ", NamedTextColor.GRAY))
                .append(Component.text("500 Coins", NamedTextColor.GOLD))
                .build());
    }

    private static void assertBalanced(String paddedText, Component unpadded) {
        int leadingSpaces = paddedText.length() - paddedText.stripLeading().length();
        int remaining = 308 - ChatFontMetrics.width(unpadded);
        int padding = leadingSpaces * ChatFontMetrics.spaceWidth();

        assertTrue(padding <= remaining / 2);
        assertTrue(remaining / 2 - padding < ChatFontMetrics.spaceWidth());
    }

    private static Component renderChat(MessageBlock block) {
        return DefaultMessageRenderer.INSTANCE.renderBlock(block, RenderTarget.CHAT);
    }

    private static Component renderHover(MessageBlock block) {
        return DefaultMessageRenderer.INSTANCE.renderBlock(block, RenderTarget.HOVER);
    }

    private static String plain(Component component) {
        StringBuilder result = new StringBuilder();
        appendPlain(component, result);
        return result.toString();
    }

    private static void appendPlain(Component component, StringBuilder result) {
        if (component instanceof TextComponent text) {
            result.append(text.content());
        }
        for (Component child : component.children()) {
            appendPlain(child, result);
        }
    }

    private static List<TextComponent> textNodes(Component component) {
        List<TextComponent> result = new ArrayList<>();
        collectTextNodes(component, result);
        return result;
    }

    private static void collectTextNodes(Component component, List<TextComponent> result) {
        if (component instanceof TextComponent text) {
            result.add(text);
        }
        for (Component child : component.children()) {
            collectTextNodes(child, result);
        }
    }
}
