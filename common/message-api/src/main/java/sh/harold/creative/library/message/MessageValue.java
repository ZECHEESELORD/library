package sh.harold.creative.library.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import sh.harold.creative.library.ui.value.UiValue;

import java.util.Objects;
import java.util.Optional;

public final class MessageValue {

    private final Component component;
    private final TextColor colorOverride;
    private final Click clickAction;
    private final MessageBlock hoverBlock;

    private MessageValue(Component component, TextColor colorOverride, Click clickAction, MessageBlock hoverBlock) {
        this.component = Objects.requireNonNull(component, "component");
        this.colorOverride = colorOverride;
        this.clickAction = clickAction;
        this.hoverBlock = hoverBlock;
    }

    public static MessageValue of(Component component) {
        return new MessageValue(component, null, null, null);
    }

    public static MessageValue of(UiValue value) {
        Objects.requireNonNull(value, "value");
        MessageValue messageValue = new MessageValue(Component.text(value.text()), null, null, null);
        if (value.colorOverride() != null) {
            return messageValue.color(value.colorOverride());
        }
        return messageValue;
    }

    public Component component() {
        return component;
    }

    public Optional<TextColor> colorOverride() {
        return Optional.ofNullable(colorOverride);
    }

    public Optional<Click> clickAction() {
        return Optional.ofNullable(clickAction);
    }

    public Optional<MessageBlock> hoverBlock() {
        return Optional.ofNullable(hoverBlock);
    }

    public MessageValue color(TextColor color) {
        return new MessageValue(component, Objects.requireNonNull(color, "color"), clickAction, hoverBlock);
    }

    public MessageValue color(int rgbHex) {
        return color(TextColor.color(requireRgb(rgbHex)));
    }

    public MessageValue click(Click click) {
        return new MessageValue(component, colorOverride, Objects.requireNonNull(click, "click"), hoverBlock);
    }

    public MessageValue hover(MessageBlock hover) {
        return new MessageValue(component, colorOverride, clickAction, Objects.requireNonNull(hover, "hover"));
    }

    private static int requireRgb(int rgbHex) {
        if (rgbHex < 0x000000 || rgbHex > 0xFFFFFF) {
            throw new IllegalArgumentException("rgbHex must be between 0x000000 and 0xFFFFFF");
        }
        return rgbHex;
    }
}
