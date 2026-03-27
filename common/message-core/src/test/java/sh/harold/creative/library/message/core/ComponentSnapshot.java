package sh.harold.creative.library.message.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class ComponentSnapshot {

    private ComponentSnapshot() {
    }

    static String snapshot(Component component) {
        StringBuilder builder = new StringBuilder();
        builder.append("TEXT:\n");
        builder.append(visibleText(component));
        builder.append("\nTREE:\n");
        appendComponent(builder, component, 0);
        return builder.toString().stripTrailing();
    }

    private static String visibleText(Component component) {
        StringBuilder builder = new StringBuilder();
        appendVisibleText(builder, component);
        return builder.toString();
    }

    private static void appendVisibleText(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        for (Component child : component.children()) {
            appendVisibleText(builder, child);
        }
    }

    private static void appendComponent(StringBuilder builder, Component component, int indent) {
        if (indent > 0 && isPassThrough(component)) {
            for (Component child : component.children()) {
                appendComponent(builder, child, indent);
            }
            return;
        }

        indent(builder, indent);
        builder.append(componentLabel(component));
        appendStyle(builder, component.style());
        ClickEvent clickEvent = component.clickEvent();
        if (clickEvent != null) {
            builder.append(" click=").append(click(clickEvent));
        }
        builder.append('\n');

        HoverEvent<?> hoverEvent = component.hoverEvent();
        if (hoverEvent != null) {
            indent(builder, indent + 2);
            builder.append("hover {\n");
            if (hoverEvent.value() instanceof Component hoverComponent) {
                appendComponent(builder, hoverComponent, indent + 4);
            } else {
                indent(builder, indent + 4);
                builder.append(String.valueOf(hoverEvent.value())).append('\n');
            }
            indent(builder, indent + 2);
            builder.append("}\n");
        }

        for (Component child : component.children()) {
            appendComponent(builder, child, indent + 2);
        }
    }

    private static boolean isPassThrough(Component component) {
        if (!(component instanceof TextComponent textComponent)) {
            return false;
        }
        return textComponent.content().isEmpty()
                && component.style().isEmpty()
                && component.clickEvent() == null
                && component.hoverEvent() == null;
    }

    private static String componentLabel(Component component) {
        if (component instanceof TextComponent textComponent) {
            return "text(\"" + escape(textComponent.content()) + "\")";
        }
        return component.getClass().getSimpleName();
    }

    private static void appendStyle(StringBuilder builder, Style style) {
        TextColor color = style.color();
        if (color != null) {
            builder.append(" color=").append(color(color));
        }
        appendDecoration(builder, style, TextDecoration.BOLD, "bold");
        appendDecoration(builder, style, TextDecoration.ITALIC, "italic");
        appendDecoration(builder, style, TextDecoration.UNDERLINED, "underlined");
        appendDecoration(builder, style, TextDecoration.STRIKETHROUGH, "strikethrough");
        appendDecoration(builder, style, TextDecoration.OBFUSCATED, "obfuscated");
    }

    private static void appendDecoration(StringBuilder builder, Style style, TextDecoration decoration, String label) {
        if (style.decoration(decoration) == TextDecoration.State.TRUE) {
            builder.append(' ').append(label);
        }
    }

    private static String click(ClickEvent clickEvent) {
        return switch (clickEvent.action()) {
            case OPEN_URL -> "open_url(" + clickEvent.value() + ")";
            case RUN_COMMAND -> "run_command(" + clickEvent.value() + ")";
            case SUGGEST_COMMAND -> "suggest_command(" + clickEvent.value() + ")";
            case CHANGE_PAGE -> "change_page(" + clickEvent.value() + ")";
            case COPY_TO_CLIPBOARD -> "copy_to_clipboard(" + clickEvent.value() + ")";
            default -> clickEvent.action().name().toLowerCase() + "(" + clickEvent.value() + ")";
        };
    }

    private static String color(TextColor color) {
        if (color.equals(NamedTextColor.BLACK)) {
            return "black";
        }
        if (color.equals(NamedTextColor.DARK_BLUE)) {
            return "dark_blue";
        }
        if (color.equals(NamedTextColor.DARK_GREEN)) {
            return "dark_green";
        }
        if (color.equals(NamedTextColor.DARK_AQUA)) {
            return "dark_aqua";
        }
        if (color.equals(NamedTextColor.DARK_RED)) {
            return "dark_red";
        }
        if (color.equals(NamedTextColor.DARK_PURPLE)) {
            return "dark_purple";
        }
        if (color.equals(NamedTextColor.GOLD)) {
            return "gold";
        }
        if (color.equals(NamedTextColor.GRAY)) {
            return "gray";
        }
        if (color.equals(NamedTextColor.DARK_GRAY)) {
            return "dark_gray";
        }
        if (color.equals(NamedTextColor.BLUE)) {
            return "blue";
        }
        if (color.equals(NamedTextColor.GREEN)) {
            return "green";
        }
        if (color.equals(NamedTextColor.AQUA)) {
            return "aqua";
        }
        if (color.equals(NamedTextColor.RED)) {
            return "red";
        }
        if (color.equals(NamedTextColor.LIGHT_PURPLE)) {
            return "light_purple";
        }
        if (color.equals(NamedTextColor.YELLOW)) {
            return "yellow";
        }
        if (color.equals(NamedTextColor.WHITE)) {
            return "white";
        }
        return color.asHexString().toUpperCase();
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void indent(StringBuilder builder, int indent) {
        for (int index = 0; index < indent; index++) {
            builder.append(' ');
        }
    }
}
