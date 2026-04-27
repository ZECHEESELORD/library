package sh.harold.creative.library.message.fabric;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sh.harold.creative.library.message.Click;
import sh.harold.creative.library.message.Message;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
