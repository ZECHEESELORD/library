package sh.harold.creative.library.example.minestom;

import sh.harold.creative.library.ui.value.UiValue;
import sh.harold.creative.library.ui.value.UiValues;

final class ExampleMessageValues {

    private static final int RANK = 0x55FF55;
    private static final int LINK = 0x55FF55;
    private static final int HIGHLIGHT = 0xFFFF55;
    private static final int COINS = 0xFFAA00;
    private static final int XP = 0x55FF55;
    private static final int UNLOCK = 0x55FFFF;

    private ExampleMessageValues() {
    }

    static UiValue rank(String label) {
        return UiValues.literal(label, RANK);
    }

    static UiValue highlight(String value) {
        return UiValues.literal(value, HIGHLIGHT);
    }

    static UiValue linkLabel(String label) {
        return UiValues.literal(label, LINK);
    }

    static UiValue coinsReward(int amount) {
        return UiValues.literal(UiValues.prettyNumber(amount).text() + " Coins", COINS);
    }

    static UiValue xpReward(int amount, String label) {
        return UiValues.literal(UiValues.prettyNumber(amount).text() + " " + label + " XP", XP);
    }

    static UiValue unlock(String value) {
        return UiValues.literal(value, UNLOCK);
    }
}
