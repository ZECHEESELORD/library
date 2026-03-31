package sh.harold.creative.library.ui.value;

import net.kyori.adventure.text.format.TextColor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

public final class UiValues {

    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat DECIMAL = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private UiValues() {
    }

    public static UiValue literal(Object value) {
        return UiValue.of(Objects.requireNonNull(value, "value"));
    }

    public static UiValue literal(Object value, TextColor color) {
        return literal(value).color(color);
    }

    public static UiValue literal(Object value, int rgbHex) {
        return literal(value).color(rgbHex);
    }

    public static UiValue prettyNumber(Number value) {
        return UiValue.of(formatNumber(value));
    }

    public static UiValue prettyNumber(Number value, TextColor color) {
        return prettyNumber(value).color(color);
    }

    public static UiValue prettyNumber(Number value, int rgbHex) {
        return prettyNumber(value).color(rgbHex);
    }

    public static UiValue prettyPercent(Number value) {
        return UiValue.of(formatNumber(value) + "%");
    }

    public static UiValue prettyPercent(Number value, TextColor color) {
        return prettyPercent(value).color(color);
    }

    public static UiValue prettyPercent(Number value, int rgbHex) {
        return prettyPercent(value).color(rgbHex);
    }

    private static String formatNumber(Number value) {
        BigDecimal decimal = asBigDecimal(value, "value").stripTrailingZeros();
        if (decimal.scale() <= 0) {
            return WHOLE.format(decimal);
        }
        return DECIMAL.format(decimal.setScale(Math.min(decimal.scale(), 2), RoundingMode.HALF_UP));
    }

    private static BigDecimal asBigDecimal(Number value, String label) {
        Objects.requireNonNull(value, label);
        return new BigDecimal(String.valueOf(value));
    }

}
