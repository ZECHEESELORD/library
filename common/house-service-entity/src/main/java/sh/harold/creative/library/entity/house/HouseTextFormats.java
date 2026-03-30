package sh.harold.creative.library.entity.house;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

final class HouseTextFormats {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final TextColor DEFAULT_NAME_COLOR = NamedTextColor.WHITE;
    private static final TextColor DESCRIPTION_COLOR = NamedTextColor.GRAY;
    private static final Component CLICK = Component.text("CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD);

    private HouseTextFormats() {
    }

    static Component parse(String input) {
        Objects.requireNonNull(input, "input");
        if (input.indexOf('&') >= 0) {
            return MINI_MESSAGE.deserialize(MINI_MESSAGE.serialize(LEGACY.deserialize(input)));
        }
        return MINI_MESSAGE.deserialize(input);
    }

    static Component displayName(Component name) {
        return applyColor(name, DEFAULT_NAME_COLOR, false);
    }

    static Component description(Component description) {
        Component line = Component.text()
                .append(Component.text("["))
                .append(description)
                .append(Component.text("]"))
                .build();
        return applyColor(line, DESCRIPTION_COLOR, true);
    }

    static Component prompt() {
        return CLICK;
    }

    static Component anchorName(UUID id) {
        Objects.requireNonNull(id, "id");
        String shortId = id.toString().replace("-", "").substring(0, 8);
        return Component.text("[NPC] " + shortId, NamedTextColor.DARK_GRAY);
    }

    private static Component applyColor(Component component, TextColor color, boolean overwrite) {
        Component recolored = overwrite ? component.color(color) : component.colorIfAbsent(color);
        List<Component> children = component.children();
        if (children.isEmpty()) {
            return recolored;
        }
        List<Component> recoloredChildren = new ArrayList<>(children.size());
        for (Component child : children) {
            recoloredChildren.add(applyColor(child, color, overwrite));
        }
        return recolored.children(recoloredChildren);
    }
}
