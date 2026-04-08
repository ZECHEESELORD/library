package sh.harold.creative.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.MenuClick;
import sh.harold.creative.library.menu.ReactiveListControlState;
import sh.harold.creative.library.menu.ReactiveListControls;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveMenuInput;
import sh.harold.creative.library.menu.ReactiveTextPromptMode;
import sh.harold.creative.library.menu.ReactiveTextPromptRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveListControlsTest {

    private static final ReactiveTextPromptRequest SEARCH_PROMPT =
            ReactiveTextPromptRequest.chat("search", "Search effects", "");

    @Test
    void reduceOpenSearchPromptEmitsPromptEffect() {
        ReactiveListControlState state = new ReactiveListControlState();

        ReactiveListControls.Update update = ReactiveListControls.reduce(
                state,
                new ReactiveMenuInput.Click(50, MenuClick.LEFT, false, new ReactiveListControls.Action.OpenSearchPrompt()),
                SEARCH_PROMPT,
                3,
                2);

        assertFalse(update.changed());
        ReactiveMenuEffect.RequestTextPrompt effect =
                assertInstanceOf(ReactiveMenuEffect.RequestTextPrompt.class, update.effects().getFirst());
        assertEquals(SEARCH_PROMPT, effect.request());
    }

    @Test
    void reduceSubmittedPromptNormalizesSearchQuery() {
        ReactiveListControlState state = new ReactiveListControlState();

        ReactiveListControls.Update update = ReactiveListControls.reduce(
                state,
                new ReactiveMenuInput.TextPromptSubmitted("search", "  pain   bow  ", ReactiveTextPromptMode.CHAT),
                SEARCH_PROMPT,
                3,
                2);

        assertTrue(update.changed());
        assertEquals("pain bow", state.searchQuery());
        assertTrue(update.effects().isEmpty());
    }

    @Test
    void reduceCyclesFilterAndSortIndices() {
        ReactiveListControlState state = new ReactiveListControlState();
        state.filterIndex(1);
        state.sortIndex(1);

        ReactiveListControls.Update previousFilter = ReactiveListControls.reduce(
                state,
                new ReactiveMenuInput.Click(51, MenuClick.RIGHT, false, new ReactiveListControls.Action.PreviousFilter()),
                SEARCH_PROMPT,
                3,
                2);
        ReactiveListControls.Update nextSort = ReactiveListControls.reduce(
                state,
                new ReactiveMenuInput.Click(52, MenuClick.LEFT, false, new ReactiveListControls.Action.NextSort()),
                SEARCH_PROMPT,
                3,
                2);

        assertTrue(previousFilter.changed());
        assertTrue(nextSort.changed());
        assertEquals(0, state.filterIndex());
        assertEquals(0, state.sortIndex());
    }

    @Test
    void matchesSearchRequiresEveryTokenAcrossFields() {
        assertTrue(ReactiveListControls.matchesSearch("pain bow", List.of("Painful Bow", "Dungeon Drop")));
        assertFalse(ReactiveListControls.matchesSearch("pain wand", List.of("Painful Bow", "Dungeon Drop")));
    }
}
