package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.MenuPair;
import sh.harold.creative.library.menu.MenuValueLine;
import sh.harold.creative.library.ui.value.UiValue;
import sh.harold.creative.library.ui.value.UiValues;

import java.util.List;

final class FakeSkyBlockMenuValues {

    private static final int SPEED = 0xFFFFFF;
    private static final int STRENGTH = 0xFF5555;
    private static final int DEFENSE = 0x55FF55;
    private static final int CRIT_DAMAGE = 0x5555FF;
    private static final int CRIT_CHANCE = 0x55FFFF;
    private static final int HEALTH = 0xFF5555;
    private static final int INTELLIGENCE = 0x55FFFF;
    private static final int XP = 0x55FF55;
    private static final int GEMS = 0x55FF55;

    private FakeSkyBlockMenuValues() {
    }

    static List<MenuValueLine> profileStats() {
        return List.of(
                MenuValueLine.of("✦ Speed ", UiValues.prettyNumber(361, SPEED)),
                MenuValueLine.of("❁ Strength ", UiValues.prettyNumber(399.75, STRENGTH)),
                MenuValueLine.of("❈ Defense ", UiValues.prettyNumber(786, DEFENSE)),
                MenuValueLine.of("☠ Crit Damage ", UiValues.prettyPercent(306.5, CRIT_DAMAGE)),
                MenuValueLine.of("☣ Crit Chance ", UiValues.prettyPercent(144.5, CRIT_CHANCE)),
                MenuValueLine.of("❤ Health ", UiValues.prettyNumber(2_524, HEALTH)),
                MenuValueLine.of("✎ Intelligence ", UiValues.prettyNumber(1_563.14, INTELLIGENCE)),
                MenuValueLine.of("", UiValues.literal("and more...")));
    }

    static UiValue totalXp(int current, int max) {
        return ratio(current, max, XP);
    }

    static UiValue milestone(int current, int max) {
        return ratio(current, max, XP);
    }

    static UiValue skyBlockGems(int amount) {
        return suffixedNumber(amount, "SkyBlock Gems", GEMS);
    }

    static UiValue gems(int amount) {
        return suffixedNumber(amount, "Gems", GEMS);
    }

    private static UiValue ratio(int current, int max, int color) {
        return UiValues.literal(UiValues.prettyNumber(current).text() + "/" + UiValues.prettyNumber(max).text(), color);
    }

    private static UiValue suffixedNumber(int amount, String suffix, int color) {
        return UiValues.literal(UiValues.prettyNumber(amount).text() + " " + suffix, color);
    }

    static MenuPair detail(String key, Object value) {
        return MenuPair.of(key, UiValues.literal(value));
    }
}
