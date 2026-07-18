package sh.harold.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ReactiveMenuEffect;
import sh.harold.library.menu.ReactiveMenuResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveMenuResultTest {

    @Test
    void unchangedCarriesNeitherStateUpdateNorEffect() {
        ReactiveMenuResult<String> result = ReactiveMenuResult.unchanged();

        assertFalse(result.stateChanged());
        assertNull(result.state());
        assertTrue(result.effect().isEmpty());
    }

    @Test
    void effectDoesNotImplicitlyReplaceState() {
        ReactiveMenuEffect effect = new ReactiveMenuEffect.Close();

        ReactiveMenuResult<String> result = ReactiveMenuResult.effect(effect);

        assertFalse(result.stateChanged());
        assertSame(effect, result.effect().orElseThrow());
    }

    @Test
    void updateCanCarryOneEffect() {
        ReactiveMenuEffect effect = new ReactiveMenuEffect.Close();

        ReactiveMenuResult<String> result = ReactiveMenuResult.update("next", effect);

        assertTrue(result.stateChanged());
        assertEquals("next", result.state());
        assertSame(effect, result.effect().orElseThrow());
    }

    @Test
    void effectFactoriesRejectNull() {
        assertThrows(NullPointerException.class, () -> ReactiveMenuResult.effect(null));
        assertThrows(NullPointerException.class, () -> ReactiveMenuResult.update("next", null));
    }
}
