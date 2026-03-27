package sh.harold.creative.library.message.core;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.TextColor;
import sh.harold.creative.library.message.MessageBlock;
import sh.harold.creative.library.message.MessageBlockBuilder;
import sh.harold.creative.library.message.SlotBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

public final class DefaultMessageBlockBuilder implements MessageBlockBuilder {

    private final List<CompiledBlockEntry> entries = new ArrayList<>();

    @Override
    public MessageBlockBuilder title(String text, TextColor color) {
        entries.add(new CompiledBlockEntry(new MessageBlock.TitleEntry(text, Objects.requireNonNull(color, "color")), null));
        return this;
    }

    @Override
    public MessageBlockBuilder title(String text, int rgbHex) {
        return title(text, TextColor.color(requireRgb(rgbHex)));
    }

    @Override
    public MessageBlockBuilder blank() {
        entries.add(new CompiledBlockEntry(new MessageBlock.BlankEntry(), null));
        return this;
    }

    @Override
    public MessageBlockBuilder line(String template, SlotBinding... slots) {
        java.util.Map<String, sh.harold.creative.library.message.MessageValue> bindings = MessageBindings.fromSlots(slots);
        CompiledTemplate compiledTemplate = CompiledTemplate.parse(template);
        compiledTemplate.validate(bindings);
        entries.add(new CompiledBlockEntry(new MessageBlock.LineEntry(template, bindings), compiledTemplate));
        return this;
    }

    @Override
    public MessageBlockBuilder bullet(String template, SlotBinding... slots) {
        java.util.Map<String, sh.harold.creative.library.message.MessageValue> bindings = MessageBindings.fromSlots(slots);
        CompiledTemplate compiledTemplate = CompiledTemplate.parse(template);
        compiledTemplate.validate(bindings);
        entries.add(new CompiledBlockEntry(new MessageBlock.BulletEntry(template, bindings), compiledTemplate));
        return this;
    }

    @Override
    public <T> MessageBlockBuilder bullets(Iterable<T> items, BiFunction<MessageBlockBuilder, T, MessageBlockBuilder> mapper) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(mapper, "mapper");

        BulletOnlyBuilder bulletBuilder = new BulletOnlyBuilder();
        for (T item : items) {
            MessageBlockBuilder returnedBuilder = Objects.requireNonNull(
                    mapper.apply(bulletBuilder, item),
                    "mapper result"
            );
            if (returnedBuilder != bulletBuilder) {
                throw new IllegalArgumentException("bullets(...) mapper must return the provided builder");
            }
        }
        return this;
    }

    @Override
    public void send(Audience audience) {
        build().send(audience);
    }

    @Override
    public MessageBlock build() {
        return new DefaultMessageBlock(List.copyOf(entries));
    }

    private static int requireRgb(int rgbHex) {
        if (rgbHex < 0x000000 || rgbHex > 0xFFFFFF) {
            throw new IllegalArgumentException("rgbHex must be between 0x000000 and 0xFFFFFF");
        }
        return rgbHex;
    }

    private final class BulletOnlyBuilder implements MessageBlockBuilder {

        @Override
        public MessageBlockBuilder title(String text, TextColor color) {
            throw new IllegalStateException("bullets(...) mapper may only add bullet entries");
        }

        @Override
        public MessageBlockBuilder title(String text, int rgbHex) {
            throw new IllegalStateException("bullets(...) mapper may only add bullet entries");
        }

        @Override
        public MessageBlockBuilder blank() {
            throw new IllegalStateException("bullets(...) mapper may only add bullet entries");
        }

        @Override
        public MessageBlockBuilder line(String template, SlotBinding... slots) {
            throw new IllegalStateException("bullets(...) mapper may only add bullet entries");
        }

        @Override
        public MessageBlockBuilder bullet(String template, SlotBinding... slots) {
            DefaultMessageBlockBuilder.this.bullet(template, slots);
            return this;
        }

        @Override
        public <T> MessageBlockBuilder bullets(Iterable<T> items, BiFunction<MessageBlockBuilder, T, MessageBlockBuilder> mapper) {
            Objects.requireNonNull(items, "items");
            Objects.requireNonNull(mapper, "mapper");
            for (T item : items) {
                MessageBlockBuilder returnedBuilder = Objects.requireNonNull(
                        mapper.apply(this, item),
                        "mapper result"
                );
                if (returnedBuilder != this) {
                    throw new IllegalArgumentException("bullets(...) mapper must return the provided builder");
                }
            }
            return this;
        }

        @Override
        public void send(Audience audience) {
            throw new IllegalStateException("bullets(...) mapper may only add bullet entries");
        }

        @Override
        public MessageBlock build() {
            throw new IllegalStateException("bullets(...) mapper may only add bullet entries");
        }
    }
}
