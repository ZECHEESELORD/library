package sh.harold.library.menu.minestom;

import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import org.junit.jupiter.api.Test;
import sh.harold.library.menu.core.StandardMenuService;
import sh.harold.library.sound.core.SoundCueScheduler;
import sh.harold.library.sound.core.StandardSoundCueService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinestomMenuPlatformTest {

    @Test
    void closedPlatformCannotOpenAnUnmanagedInventory() {
        MinestomMenuPlatform menus = new MinestomMenuPlatform(
                new StandardMenuService(),
                EventNode.all("closed-menu-platform"),
                new StandardSoundCueService(SoundCueScheduler.unsupported()));
        menus.close();

        assertThrows(IllegalStateException.class, () -> menus.open(null, null));
        assertDoesNotThrow(menus::close);
    }
}
