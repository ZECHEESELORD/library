package sh.harold.library.menu.core;

import org.junit.jupiter.api.Test;
import sh.harold.library.menu.ReactiveMenuEffect;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactiveMenuEffectTest {

    @Test
    void replaceEffectRejectsNullMenu() {
        assertThrows(NullPointerException.class, () -> new ReactiveMenuEffect.Replace(null));
    }
}
