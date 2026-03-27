package sh.harold.creative.library.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.TextColor;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface MessageBlockBuilder {

    MessageBlockBuilder title(String text, TextColor color);

    MessageBlockBuilder title(String text, int rgbHex);

    MessageBlockBuilder blank();

    MessageBlockBuilder line(String template, SlotBinding... slots);

    MessageBlockBuilder bullet(String template, SlotBinding... slots);

    default MessageBlockBuilder bullets(String... lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            bullet(line);
        }
        return this;
    }

    default MessageBlockBuilder bullets(Iterable<String> lines) {
        Objects.requireNonNull(lines, "lines");
        for (String line : lines) {
            bullet(line);
        }
        return this;
    }

    default <T> MessageBlockBuilder bullets(Iterable<T> items, Function<T, String> formatter) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(formatter, "formatter");
        for (T item : items) {
            bullet(formatter.apply(item));
        }
        return this;
    }

    <T> MessageBlockBuilder bullets(Iterable<T> items, BiFunction<MessageBlockBuilder, T, MessageBlockBuilder> mapper);

    void send(Audience audience);

    MessageBlock build();
}
