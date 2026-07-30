package sh.harold.library.menu;

import net.kyori.adventure.key.Key;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Mutable authoring surface used while a {@link MenuItemTemplate} selects an item state.
 */
public final class MenuItemDraft extends AbstractMenuItemBuilder<MenuItemDraft> {

    private final Map<MenuClick, MenuInteraction> interactions = new EnumMap<>(MenuClick.class);
    private int amount = 1;
    private boolean promptSuppressed;

    MenuItemDraft(MenuIcon icon) {
        super(icon);
    }

    public MenuItemDraft interaction(MenuClick click, MenuInteraction interaction) {
        interactions.put(Objects.requireNonNull(click, "click"), Objects.requireNonNull(interaction, "interaction"));
        return this;
    }

    public MenuItemDraft action(ActionVerb verb, MenuAction action) {
        return action(verb, verb.promptLabel(), action);
    }

    public MenuItemDraft action(ActionVerb verb, String promptLabel, MenuAction action) {
        return interaction(MenuClick.LEFT,
                MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
    }

    public MenuItemDraft onLeftClick(ActionVerb verb, MenuAction action) {
        return action(verb, action);
    }

    public MenuItemDraft onLeftClick(ActionVerb verb, String promptLabel, MenuAction action) {
        return action(verb, promptLabel, action);
    }

    public MenuItemDraft onRightClick(ActionVerb verb, MenuAction action) {
        return onRightClick(verb, verb.promptLabel(), action);
    }

    public MenuItemDraft onRightClick(ActionVerb verb, String promptLabel, MenuAction action) {
        return interaction(MenuClick.RIGHT,
                MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
    }

    public MenuItemDraft onShiftLeftClick(ActionVerb verb, MenuAction action) {
        return onShiftLeftClick(verb, verb.promptLabel(), action);
    }

    public MenuItemDraft onShiftLeftClick(ActionVerb verb, String promptLabel, MenuAction action) {
        return interaction(MenuClick.SHIFT_LEFT,
                MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
    }

    public MenuItemDraft onShiftRightClick(ActionVerb verb, MenuAction action) {
        return onShiftRightClick(verb, verb.promptLabel(), action);
    }

    public MenuItemDraft onShiftRightClick(ActionVerb verb, String promptLabel, MenuAction action) {
        return interaction(MenuClick.SHIFT_RIGHT,
                MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Execute(action)));
    }

    public MenuItemDraft emit(ActionVerb verb, Object message) {
        return emit(verb, verb.promptLabel(), message);
    }

    public MenuItemDraft emit(ActionVerb verb, String promptLabel, Object message) {
        return interaction(MenuClick.LEFT,
                MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Dispatch(message)));
    }

    public MenuItemDraft onRightEmit(ActionVerb verb, Object message) {
        return onRightEmit(verb, verb.promptLabel(), message);
    }

    public MenuItemDraft onRightEmit(ActionVerb verb, String promptLabel, Object message) {
        return interaction(MenuClick.RIGHT,
                MenuInteraction.of(verb, promptLabel, new MenuSlotAction.Dispatch(message)));
    }

    public MenuItemDraft sound(Key soundCueKey) {
        return leftSound(soundCueKey);
    }

    public MenuItemDraft sound(String soundCueKey) {
        return leftSound(Key.key(soundCueKey));
    }

    public MenuItemDraft leftSound(Key soundCueKey) {
        return updateInteraction(MenuClick.LEFT, value -> value.withSound(soundCueKey),
                "sound(...) requires a left-click interaction");
    }

    public MenuItemDraft leftSound(String soundCueKey) {
        return leftSound(Key.key(soundCueKey));
    }

    public MenuItemDraft rightSound(Key soundCueKey) {
        return updateInteraction(MenuClick.RIGHT, value -> value.withSound(soundCueKey),
                "rightSound(...) requires a right-click interaction");
    }

    public MenuItemDraft rightSound(String soundCueKey) {
        return rightSound(Key.key(soundCueKey));
    }

    public MenuItemDraft withoutSound() {
        return updateInteraction(MenuClick.LEFT, MenuInteraction::withoutSound,
                "withoutSound() requires a left-click interaction");
    }

    public MenuItemDraft withoutRightSound() {
        return updateInteraction(MenuClick.RIGHT, MenuInteraction::withoutSound,
                "withoutRightSound() requires a right-click interaction");
    }

    public MenuItemDraft skipPrompt() {
        promptSuppressed = true;
        return this;
    }

    public MenuItemDraft amount(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        this.amount = amount;
        return this;
    }

    MenuItem freeze() {
        if (interactions.isEmpty()) {
            return new MenuDisplayItem(icon(), name(), secondary(), sections(), statusLines(), exactLore(),
                    isGlowing(), amount, tooltipBehavior());
        }
        return new MenuButton(icon(), name(), secondary(), sections(), statusLines(), exactLore(),
                isGlowing(), amount, interactions, promptSuppressed, tooltipBehavior());
    }

    private MenuItemDraft updateInteraction(
            MenuClick click,
            UnaryOperator<MenuInteraction> transform,
            String failureMessage
    ) {
        MenuInteraction interaction = interactions.get(click);
        if (interaction == null) {
            throw new IllegalStateException(failureMessage);
        }
        interactions.put(click, transform.apply(interaction));
        return this;
    }

    @Override
    protected MenuItemDraft self() {
        return this;
    }
}
