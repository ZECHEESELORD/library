package sh.harold.creative.library.menu;

import java.util.Objects;

public record MenuInteraction(ActionVerb verb, String promptLabel, MenuSlotAction action) {

    public MenuInteraction {
        verb = Objects.requireNonNull(verb, "verb");
        Objects.requireNonNull(promptLabel, "promptLabel");
        if (promptLabel.isBlank()) {
            throw new IllegalArgumentException("promptLabel cannot be blank");
        }
        action = Objects.requireNonNull(action, "action");
    }

    public static MenuInteraction of(ActionVerb verb, MenuSlotAction action) {
        return new MenuInteraction(verb, verb.promptLabel(), action);
    }
}
