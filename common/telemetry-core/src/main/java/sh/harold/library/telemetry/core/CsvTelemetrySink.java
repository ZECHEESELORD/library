package sh.harold.library.telemetry.core;

import sh.harold.library.telemetry.FrameTime;
import sh.harold.library.telemetry.TelemetrySink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Writes telemetry as CSV: a header {@code tick,partial,<columns...>}, then one line per row. Numbers are
 * locale independent, doubles keep 6 decimals with trailing zeros dropped, booleans are {@code true} or
 * {@code false}, empty cells mean "no value", and text is quoted when it needs to be (RFC 4180).
 */
public final class CsvTelemetrySink implements TelemetrySink {

    private final Writer writer;

    public CsvTelemetrySink(Writer writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** Opens (and truncates) {@code file}, creating parent directories. */
    public static CsvTelemetrySink open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return new CsvTelemetrySink(new BufferedWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new UncheckedIOException("cannot open telemetry file " + file, exception);
        }
    }

    @Override
    public void header(List<String> columns) {
        StringBuilder line = new StringBuilder("tick,partial");
        for (String column : columns) {
            line.append(',').append(quote(column));
        }
        write(line.append('\n').toString());
    }

    @Override
    public void row(FrameTime time, Object[] values) {
        write(formatRow(time, values));
    }

    /** One CSV line for {@code time} and {@code values}, ending in a newline. */
    public static String formatRow(FrameTime time, Object[] values) {
        StringBuilder line = new StringBuilder();
        line.append(time.tick()).append(',').append(formatDouble(time.partial()));
        for (Object value : values) {
            line.append(',').append(formatValue(value));
        }
        return line.append('\n').toString();
    }

    public static String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double d) {
            return formatDouble(d);
        }
        if (value instanceof Float f) {
            return formatDouble(f.doubleValue());
        }
        if (value instanceof String s) {
            return quote(s);
        }
        return value.toString();
    }

    public static String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "";
        }
        BigDecimal decimal = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_EVEN).stripTrailingZeros();
        if (decimal.signum() == 0) {
            return "0";
        }
        return decimal.toPlainString();
    }

    private static String quote(String text) {
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    @Override
    public void flush() {
        try {
            writer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Override
    public void close() {
        try {
            writer.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void write(String text) {
        try {
            writer.write(text);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
