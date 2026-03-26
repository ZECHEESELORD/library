package sh.harold.creative.library.menu.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

final class HouseNumberFormatter {

    private static final DecimalFormat WHOLE = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat DECIMAL = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat PERCENT = new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.US));

    private HouseNumberFormatter() {
    }

    static String format(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() <= 0) {
            return WHOLE.format(normalized);
        }
        return DECIMAL.format(normalized.setScale(Math.min(normalized.scale(), 2), RoundingMode.HALF_UP));
    }

    static String formatPercent(BigDecimal ratio) {
        return PERCENT.format(ratio.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros());
    }
}
