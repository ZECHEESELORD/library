package sh.harold.creative.library.sound.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.sound.PackRegistrationResult;
import sh.harold.creative.library.sound.SoundCue;
import sh.harold.creative.library.sound.SoundCueKeys;
import sh.harold.creative.library.sound.SoundCuePack;
import sh.harold.creative.library.sound.SoundCuePacks;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static sh.harold.creative.library.sound.SoundCues.sound;

class StandardSoundCueRegistryTest {

    @Test
    void registerFailsOnDuplicateKeyWithoutExplicitOverlay() {
        StandardSoundCueRegistry registry = new StandardSoundCueRegistry();
        SoundCue cue = sound("minecraft:ui.button.click", 0.8f, 1.0f);

        registry.register(SoundCuePacks.pack("test").cue("menu/click", cue).build());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(SoundCuePacks.pack("test").cue("menu/click", cue).build())
        );

        assertEquals("Duplicate sound cue key: test:menu/click", exception.getMessage());
    }

    @Test
    void overlayReportsAndReplacesExistingKeys() {
        StandardSoundCueRegistry registry = new StandardSoundCueRegistry();
        SoundCue original = sound("minecraft:ui.button.click", 0.8f, 1.0f);
        SoundCue replacement = sound("minecraft:block.note_block.pling", 0.7f, 1.2f);
        SoundCuePack base = SoundCuePacks.pack("test")
                .cue("menu/click", original)
                .cue("reward/confirm", original)
                .build();
        SoundCuePack overlay = SoundCuePacks.pack("test")
                .cue("menu/click", replacement)
                .cue("reward/discovery", replacement)
                .build();

        registry.register(base);
        PackRegistrationResult result = registry.overlay(overlay);

        assertEquals("test", result.namespace());
        assertEquals(2, result.addedKeys().size());
        assertEquals(1, result.replacedKeys().size());
        assertEquals(Set.of(net.kyori.adventure.key.Key.key("test", "menu/click")), result.replacedKeys());
        assertSame(replacement, registry.cue("test:menu/click"));
        assertSame(replacement, registry.cue("test:reward/discovery"));
    }

    @Test
    void stockKeysAreStableAdventureKeys() {
        assertEquals("creative-library:menu/click", SoundCueKeys.MENU_CLICK.asString());
        assertEquals("creative-library:menu/scroll", SoundCueKeys.MENU_SCROLL.asString());
        assertEquals("creative-library:reward/level_up", SoundCueKeys.REWARD_LEVEL_UP.asString());
    }
}
