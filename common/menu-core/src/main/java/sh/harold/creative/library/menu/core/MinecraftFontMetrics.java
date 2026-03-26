package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashMap;
import java.util.Map;

final class MinecraftFontMetrics {

    private static final Map<Character, Integer> WIDTHS = new HashMap<>();

    static {
        put(2, "!,.:;|i");
        put(3, "'`l");
        put(4, " ()[]{}Itfk<>*");
        put(5, "\"/\\t+?-_~^");
        put(6, "0123456789ABCDEFGHJKLMNOPQRSTUVWXYZabcdefghjmnopqrstuvwxyz#$%&=@");
        put(7, "MNQRSUVWXYmnqrsuvwxyz");
    }

    private MinecraftFontMetrics() {
    }

    static int width(Component component) {
        if (component == null) {
            return 0;
        }
        return widthComponent(component, false);
    }

    static int width(String text) {
        int width = 0;
        for (char character : text.toCharArray()) {
            width += characterWidth(character, false) + 1;
        }
        return Math.max(0, width - 1);
    }

    private static int widthComponent(Component component, boolean inheritedBold) {
        boolean bold = inheritedBold || component.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        int width = 0;
        if (component instanceof TextComponent textComponent) {
            for (char character : textComponent.content().toCharArray()) {
                width += characterWidth(character, bold) + 1;
            }
        } else {
            width += width(ComponentText.flatten(component));
        }
        for (Component child : component.children()) {
            width += widthComponent(child, bold);
        }
        return Math.max(0, width == 0 ? 0 : width - 1);
    }

    private static int characterWidth(char character, boolean bold) {
        int width = WIDTHS.getOrDefault(character, 6);
        if (character == ' ') {
            return 4;
        }
        return bold ? width + 1 : width;
    }

    private static void put(int width, String characters) {
        for (char character : characters.toCharArray()) {
            WIDTHS.put(character, width);
        }
    }
}
