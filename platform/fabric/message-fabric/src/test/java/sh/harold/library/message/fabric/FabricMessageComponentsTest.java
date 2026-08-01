package sh.harold.library.message.fabric;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.library.message.Click;
import sh.harold.library.message.Message;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricMessageComponentsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void twitchLinkBlockKeepsOpenUrlAndClipboardClickEvents() {
        net.kyori.adventure.text.Component rendered = FabricMessageComponents.renderChat(Message.block()
                .title("TWITCH", 0x9146FF)
                .line("Open {url} and enter {code}.",
                        Message.slot("url", Message.value("https://example.com/device")
                                .click(Click.openUrl("https://example.com/device"))),
                        Message.slot("code", Message.value("ABCD-EFGH")
                                .click(Click.copyToClipboard("ABCD-EFGH"))))
                .build());

        List<ClickEvent> clicks = clickEvents(FabricMessageComponents.toNative(rendered));

        assertEquals(2, clicks.size());
        ClickEvent.OpenUrl openUrl = assertInstanceOf(ClickEvent.OpenUrl.class, clicks.get(0));
        assertEquals(URI.create("https://example.com/device"), openUrl.uri());
        ClickEvent.CopyToClipboard clipboard = assertInstanceOf(ClickEvent.CopyToClipboard.class, clicks.get(1));
        assertEquals("ABCD-EFGH", clipboard.value());
    }

    @Test
    void titleDurationsRoundUpToWholeTicks() {
        assertEquals(0, FabricMessageSender.toTitleTicks(Duration.ZERO));
        assertEquals(1, FabricMessageSender.toTitleTicks(Duration.ofNanos(1)));
        assertEquals(1, FabricMessageSender.toTitleTicks(Duration.ofMillis(50)));
        assertEquals(2, FabricMessageSender.toTitleTicks(Duration.ofMillis(51)));
        assertEquals(70, FabricMessageSender.toTitleTicks(Duration.ofMillis(3_500)));
        assertThrows(IllegalArgumentException.class, () ->
                FabricMessageSender.toTitleTicks(Duration.ofMillis(-1)));
    }

    @Test
    void titleComponentsKeepTextColorAndEmphasisDuringNativeConversion() {
        var message = Message.title(
                Component.text("Celestial Ridge", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("NEW AREA DISCOVERED!", NamedTextColor.GOLD)
        );

        net.minecraft.network.chat.Component title =
                FabricMessageComponents.toNative(message.title());
        net.minecraft.network.chat.Component subtitle =
                FabricMessageComponents.toNative(message.subtitle());

        assertEquals("Celestial Ridge", title.getString());
        assertEquals(0x55FFFF, title.getStyle().getColor().getValue());
        assertTrue(title.getStyle().isBold());
        assertEquals("NEW AREA DISCOVERED!", subtitle.getString());
        assertEquals(0xFFAA00, subtitle.getStyle().getColor().getValue());
    }

    private static List<ClickEvent> clickEvents(net.minecraft.network.chat.Component component) {
        List<ClickEvent> clicks = new ArrayList<>();
        collectClickEvents(component, clicks);
        return clicks;
    }

    private static void collectClickEvents(net.minecraft.network.chat.Component component, List<ClickEvent> clicks) {
        ClickEvent click = component.getStyle().getClickEvent();
        if (click != null) {
            clicks.add(click);
        }
        for (net.minecraft.network.chat.Component child : component.getSiblings()) {
            collectClickEvents(child, clicks);
        }
    }
}
