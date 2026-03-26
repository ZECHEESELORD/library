package sh.harold.creative.library.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.TextColor;

public interface MessageBlockBuilder {

    MessageBlockBuilder title(String text, TextColor color);

    MessageBlockBuilder title(String text, int rgbHex);

    MessageBlockBuilder blank();

    MessageBlockBuilder line(String template, SlotBinding... slots);

    MessageBlockBuilder bullet(String template, SlotBinding... slots);

    void send(Audience audience);

    MessageBlock build();
}
