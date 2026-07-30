package sh.harold.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class MenuComponents {

    private MenuComponents() {
    }

    static Component component(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof ComponentLike componentLike) {
            return componentLike.asComponent();
        }
        return Component.text(String.valueOf(value));
    }

    static String requireText(String text, String label) {
        Objects.requireNonNull(text, label);
        if (text.isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return text;
    }

    static Component requireContent(Component component, String label) {
        Objects.requireNonNull(component, label);
        if (isPlainTextTree(component) && plainText(component).isBlank()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return component;
    }

    static List<Component> copyComponents(Iterable<? extends ComponentLike> values, String label) {
        Objects.requireNonNull(values, label);
        List<Component> copy = new ArrayList<>();
        for (ComponentLike value : values) {
            copy.add(requireContent(Objects.requireNonNull(value, label + " entry").asComponent(), label + " entry"));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be empty");
        }
        return List.copyOf(copy);
    }

    private static boolean isPlainTextTree(Component component) {
        if (!(component instanceof TextComponent)) {
            return false;
        }
        for (Component child : component.children()) {
            if (!isPlainTextTree(child)) {
                return false;
            }
        }
        return true;
    }

    private static String plainText(Component component) {
        StringBuilder builder = new StringBuilder();
        appendPlainText(builder, component);
        return builder.toString();
    }

    private static void appendPlainText(StringBuilder builder, Component component) {
        if (component instanceof TextComponent textComponent) {
            builder.append(textComponent.content());
        }
        component.children().forEach(child -> appendPlainText(builder, child));
    }
}
