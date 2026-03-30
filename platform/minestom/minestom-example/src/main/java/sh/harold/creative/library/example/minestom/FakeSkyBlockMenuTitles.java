package sh.harold.creative.library.example.minestom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

final class FakeSkyBlockMenuTitles {

    private static final int NORMAL = 0xFFFF55;
    private static final int REWARD = 0xFFAA00;
    private static final int DANGER = 0xFF5555;
    private static final int SUCCESS = 0x55FF55;
    private static final int PERK = 0x55FFFF;
    private static final int SPECIAL = 0xFF55FF;
    private static final int LOCKED = 0xAAAAAA;

    private FakeSkyBlockMenuTitles() {
    }

    static Component normal(String title) {
        return colored(title, NORMAL);
    }

    static Component reward(String title) {
        return colored(title, REWARD);
    }

    static Component danger(String title) {
        return colored(title, DANGER);
    }

    static Component success(String title) {
        return colored(title, SUCCESS);
    }

    static Component perk(String title) {
        return colored(title, PERK);
    }

    static Component special(String title) {
        return colored(title, SPECIAL);
    }

    static Component locked(String title) {
        return colored(title, LOCKED);
    }

    private static Component colored(String title, int color) {
        return Component.text(title, TextColor.color(color));
    }
}
