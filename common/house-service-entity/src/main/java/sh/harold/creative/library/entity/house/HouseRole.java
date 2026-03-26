package sh.harold.creative.library.entity.house;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.Optional;

public final class HouseRole {

    private static final HouseRole HIDDEN = new HouseRole(null, true);

    private final Component label;
    private final boolean hidden;

    private HouseRole(Component label, boolean hidden) {
        this.label = label;
        this.hidden = hidden;
    }

    public static HouseRole of(Component label) {
        return new HouseRole(Objects.requireNonNull(label, "label"), false);
    }

    public static HouseRole hidden() {
        return HIDDEN;
    }

    public boolean isHidden() {
        return hidden;
    }

    public Optional<Component> label() {
        return Optional.ofNullable(label);
    }

    public Component lineComponent() {
        return hidden ? Component.empty() : label;
    }
}
