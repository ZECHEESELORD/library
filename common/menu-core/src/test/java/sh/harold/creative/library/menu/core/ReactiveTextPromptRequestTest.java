package sh.harold.creative.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.menu.ReactiveMenuEffect;
import sh.harold.creative.library.menu.ReactiveTextPromptMode;
import sh.harold.creative.library.menu.ReactiveTextPromptRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveTextPromptRequestTest {

    @Test
    void signPromptBuildsDefaultSignLinesFromInitialValue() {
        ReactiveTextPromptRequest request = ReactiveTextPromptRequest.sign("search", "Search effects", "pain");

        assertEquals(ReactiveTextPromptMode.SIGN, request.preferredMode());
        assertEquals(List.of("pain", "^^^^^^", "Search effects", ""), request.signLines());
    }

    @Test
    void promptFactoryDefaultsToPromptMode() {
        ReactiveTextPromptRequest request = ReactiveTextPromptRequest.prompt("search", "Search effects", "pain");

        assertEquals(ReactiveTextPromptMode.PROMPT, request.preferredMode());
        assertEquals(List.of(), request.signLines());
    }

    @Test
    void requestTextPromptEffectRejectsNullRequest() {
        assertThrows(NullPointerException.class, () -> new ReactiveMenuEffect.RequestTextPrompt(null));
    }
}
