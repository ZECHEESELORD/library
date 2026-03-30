package sh.harold.creative.library.ui.value;

import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiValuesTest {

    @Test
    void prettyNumberGroupsWholeValues() {
        assertEquals("3,522", UiValues.prettyNumber(3522).text());
    }

    @Test
    void prettyNumberKeepsUsefulDecimals() {
        assertEquals("1,563.14", UiValues.prettyNumber(1563.14).text());
        assertEquals("3,432,908.3", UiValues.prettyNumber(3_432_908.3).text());
    }

    @Test
    void prettyPercentAppendsSuffix() {
        assertEquals("84%", UiValues.prettyPercent(84).text());
        assertEquals("12.5%", UiValues.prettyPercent(12.5).text());
    }

    @Test
    void colorOverrideUsesProvidedHex() {
        assertEquals(TextColor.color(0x55FFFF), UiValues.prettyNumber(10, 0x55FFFF).colorOverride());
    }

    @Test
    void blankLiteralFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> UiValues.literal("   "));
    }
}
