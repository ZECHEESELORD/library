package sh.harold.creative.library.menu;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.sound.SoundCueKeys;

import java.util.Objects;

public record MenuInteraction(ActionVerb verb, String promptLabel, MenuSlotAction action, Key soundCueKey) {

    public MenuInteraction {
        verb = Objects.requireNonNull(verb, "verb");
        Objects.requireNonNull(promptLabel, "promptLabel");
        if (promptLabel.isBlank()) {
            throw new IllegalArgumentException("promptLabel cannot be blank");
        }
        action = Objects.requireNonNull(action, "action");
    }

    public static MenuInteraction of(ActionVerb verb, MenuSlotAction action) {
        return of(verb, verb.promptLabel(), action);
    }

    public static MenuInteraction of(ActionVerb verb, String promptLabel, MenuSlotAction action) {
        return new MenuInteraction(verb, promptLabel, action, defaultSoundCueKey(verb));
    }

    public static MenuInteraction of(ActionVerb verb, MenuSlotAction action, Key soundCueKey) {
        return of(verb, verb.promptLabel(), action, soundCueKey);
    }

    public static MenuInteraction of(ActionVerb verb, String promptLabel, MenuSlotAction action, Key soundCueKey) {
        return new MenuInteraction(verb, promptLabel, action, Objects.requireNonNull(soundCueKey, "soundCueKey"));
    }

    public MenuInteraction withSound(Key soundCueKey) {
        return new MenuInteraction(verb, promptLabel, action, Objects.requireNonNull(soundCueKey, "soundCueKey"));
    }

    public MenuInteraction withoutSound() {
        return new MenuInteraction(verb, promptLabel, action, null);
    }

    private static Key defaultSoundCueKey(ActionVerb verb) {
        return switch (verb) {
            case PREVIOUS_PAGE, NEXT_PAGE -> SoundCueKeys.MENU_SCROLL;
            case BUY, CLAIM, CONFIRM -> SoundCueKeys.RESULT_CONFIRM;
            default -> SoundCueKeys.MENU_CLICK;
        };
    }
}
