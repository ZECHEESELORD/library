package sh.harold.creative.library.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.harold.creative.library.ui.value.UiValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ReactiveListControls {

    public static final UtilitySlot SEARCH_SLOT = UtilitySlot.RIGHT_1;
    public static final UtilitySlot FILTER_SLOT = UtilitySlot.RIGHT_2;
    public static final UtilitySlot SORT_SLOT = UtilitySlot.RIGHT_3;

    private ReactiveListControls() {
    }

    public sealed interface Action permits Action.ClearSearch, Action.NextFilter, Action.NextSort,
            Action.OpenSearchPrompt, Action.PreviousFilter, Action.PreviousSort {

        record OpenSearchPrompt() implements Action {
        }

        record ClearSearch() implements Action {
        }

        record NextFilter() implements Action {
        }

        record PreviousFilter() implements Action {
        }

        record NextSort() implements Action {
        }

        record PreviousSort() implements Action {
        }
    }

    public record Update(boolean changed, List<ReactiveMenuEffect> effects) {

        public Update {
            effects = List.copyOf(effects);
        }

        public static Update unchanged() {
            return new Update(false, List.of());
        }

        public static Update modified() {
            return new Update(true, List.of());
        }

        public static Update effect(ReactiveMenuEffect effect) {
            return new Update(false, List.of(effect));
        }
    }

    public static MenuButton searchButton(String description, String activeQuery) {
        Objects.requireNonNull(description, "description");
        String normalizedQuery = activeQuery == null ? "" : activeQuery;
        MenuButton.Builder builder = MenuButton.builder(MenuIcon.vanilla("oak_sign"))
                .name(Component.text("Search", NamedTextColor.GREEN))
                .description(description)
                .emit(ActionVerb.BROWSE, "search effects", new Action.OpenSearchPrompt())
                .onRightEmit(ActionVerb.BROWSE, "clear search", new Action.ClearSearch());
        if (!normalizedQuery.isBlank()) {
            builder.valueLine("Filtered: ", UiValue.of(normalizedQuery).color(0xFFFF55));
        }
        return builder.build();
    }

    public static MenuButton filterButton(String description, Iterable<MenuOptionLine> options) {
        return cycleButton("Filter", "hopper", 0xFFAA00, description, options,
                new Action.NextFilter(), "cycle filter forward",
                new Action.PreviousFilter(), "cycle filter backward");
    }

    public static MenuButton sortButton(String description, Iterable<MenuOptionLine> options) {
        return cycleButton("Sort", "comparator", 0x55FFFF, description, options,
                new Action.NextSort(), "cycle sort forward",
                new Action.PreviousSort(), "cycle sort backward");
    }

    public static Update reduce(
            ReactiveListControlState state,
            ReactiveMenuInput input,
            ReactiveTextPromptRequest searchPrompt,
            int filterCount,
            int sortCount
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(searchPrompt, "searchPrompt");
        if (input instanceof ReactiveMenuInput.Click click && click.message() instanceof Action action) {
            return reduceClick(state, action, searchPrompt, filterCount, sortCount);
        }
        if (input instanceof ReactiveMenuInput.TextPromptSubmitted submitted && submitted.key().equals(searchPrompt.key())) {
            String normalized = ReactiveListControlState.normalizeSearchQuery(submitted.value());
            if (state.searchQuery().equals(normalized)) {
                return Update.unchanged();
            }
            state.searchQuery(normalized);
            return Update.modified();
        }
        return Update.unchanged();
    }

    public static boolean matchesSearch(String searchQuery, Iterable<String> fields) {
        Objects.requireNonNull(searchQuery, "searchQuery");
        Objects.requireNonNull(fields, "fields");
        String normalizedQuery = ReactiveListControlState.normalizeSearchQuery(searchQuery);
        if (normalizedQuery.isBlank()) {
            return true;
        }
        List<String> normalizedFields = new ArrayList<>();
        for (String field : fields) {
            if (field == null || field.isBlank()) {
                continue;
            }
            normalizedFields.add(field.toLowerCase(Locale.ROOT));
        }
        if (normalizedFields.isEmpty()) {
            return false;
        }
        String[] tokens = normalizedQuery.toLowerCase(Locale.ROOT).split(" ");
        for (String token : tokens) {
            boolean matched = false;
            for (String field : normalizedFields) {
                if (field.contains(token)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private static MenuButton cycleButton(
            String title,
            String iconKey,
            int titleColor,
            String description,
            Iterable<MenuOptionLine> options,
            Object leftMessage,
            String leftPrompt,
            Object rightMessage,
            String rightPrompt
    ) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(iconKey, "iconKey");
        Objects.requireNonNull(description, "description");
        List<MenuOptionLine> copiedOptions = copyOptions(options);
        MenuButton.Builder builder = MenuButton.builder(MenuIcon.vanilla(iconKey))
                .name(Component.text(title, net.kyori.adventure.text.format.TextColor.color(titleColor)))
                .description(description)
                .optionLines(copiedOptions)
                .emit(ActionVerb.BROWSE, leftPrompt, leftMessage)
                .onRightEmit(ActionVerb.BROWSE, rightPrompt, rightMessage);
        return builder.build();
    }

    private static Update reduceClick(
            ReactiveListControlState state,
            Action action,
            ReactiveTextPromptRequest searchPrompt,
            int filterCount,
            int sortCount
    ) {
        return switch (action) {
            case Action.OpenSearchPrompt ignored -> Update.effect(new ReactiveMenuEffect.RequestTextPrompt(searchPrompt));
            case Action.ClearSearch ignored -> {
                if (state.searchQuery().isBlank()) {
                    yield Update.unchanged();
                }
                state.searchQuery("");
                yield Update.modified();
            }
            case Action.NextFilter ignored -> cycleForward(state, filterCount, true);
            case Action.PreviousFilter ignored -> cycleBackward(state, filterCount, true);
            case Action.NextSort ignored -> cycleForward(state, sortCount, false);
            case Action.PreviousSort ignored -> cycleBackward(state, sortCount, false);
        };
    }

    private static Update cycleForward(ReactiveListControlState state, int count, boolean filter) {
        if (count <= 1) {
            return Update.unchanged();
        }
        if (filter) {
            state.filterIndex((state.filterIndex() + 1) % count);
        } else {
            state.sortIndex((state.sortIndex() + 1) % count);
        }
        return Update.modified();
    }

    private static Update cycleBackward(ReactiveListControlState state, int count, boolean filter) {
        if (count <= 1) {
            return Update.unchanged();
        }
        if (filter) {
            state.filterIndex((state.filterIndex() + count - 1) % count);
        } else {
            state.sortIndex((state.sortIndex() + count - 1) % count);
        }
        return Update.modified();
    }

    private static List<MenuOptionLine> copyOptions(Iterable<MenuOptionLine> options) {
        Objects.requireNonNull(options, "options");
        List<MenuOptionLine> copied = new ArrayList<>();
        for (MenuOptionLine option : options) {
            copied.add(Objects.requireNonNull(option, "option"));
        }
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("options cannot be empty");
        }
        return List.copyOf(copied);
    }
}
