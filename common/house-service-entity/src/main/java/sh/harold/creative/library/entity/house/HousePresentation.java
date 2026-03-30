package sh.harold.creative.library.entity.house;

import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Objects;

public record HousePresentation(Component name, Component description, Component prompt) {

    public HousePresentation {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(prompt, "prompt");
    }

    public List<Component> lines() {
        return List.of(name, description, prompt);
    }
}
