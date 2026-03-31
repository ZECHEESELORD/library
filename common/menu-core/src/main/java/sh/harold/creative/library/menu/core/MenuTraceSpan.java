package sh.harold.creative.library.menu.core;

import sh.harold.creative.library.menu.MenuTraceSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MenuTraceSpan {

    private final MenuTraceSnapshot snapshot;
    private final Consumer<String> sink;
    private final String host;
    private final UUID viewerId;
    private final String cause;
    private final long startedNanos = System.nanoTime();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Map<String, Long> counts = new LinkedHashMap<>();
    private final Map<String, Long> durations = new LinkedHashMap<>();
    private final Map<String, SlowDetail> slowDetails = new LinkedHashMap<>();

    private boolean matchedMenu;
    private int retained;
    private boolean finished;
    private boolean emitted;

    MenuTraceSpan(MenuTraceSnapshot snapshot, Consumer<String> sink, String host, UUID viewerId, String cause) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.host = Objects.requireNonNull(host, "host");
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.cause = Objects.requireNonNull(cause, "cause");
        field("host", host);
        field("viewer", viewerId);
        field("cause", cause);
    }

    synchronized void markMenuTitle(String menuTitle) {
        if (menuTitle == null) {
            return;
        }
        String value = menuTitle.trim();
        if (value.isEmpty()) {
            return;
        }
        fields.put("menu", value);
        if (snapshot.matchesMenuTitle(value)) {
            matchedMenu = true;
        }
    }

    synchronized void field(String key, Object value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            return;
        }
        fields.put(key, String.valueOf(value));
    }

    synchronized void setCount(String key, long value) {
        Objects.requireNonNull(key, "key");
        counts.put(key, value);
    }

    synchronized void incrementCount(String key, long delta) {
        Objects.requireNonNull(key, "key");
        counts.merge(key, delta, Long::sum);
    }

    synchronized void addDuration(String stage, long nanos) {
        Objects.requireNonNull(stage, "stage");
        durations.merge(stage, nanos, Long::sum);
    }

    synchronized void detailIfSlow(String category, long nanos, Supplier<String> messageSupplier) {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(messageSupplier, "messageSupplier");
        if (nanos < snapshot.slowDetailThresholdNanos()) {
            return;
        }
        SlowDetail existing = slowDetails.get(category);
        if (existing != null && existing.nanos() >= nanos) {
            return;
        }
        slowDetails.put(category, new SlowDetail(nanos, messageSupplier.get()));
    }

    synchronized void retain() {
        retained++;
    }

    synchronized void release() {
        if (retained == 0) {
            throw new IllegalStateException("No retained trace work to release");
        }
        retained--;
        maybeEmit();
    }

    synchronized void finish() {
        finished = true;
        maybeEmit();
    }

    private void maybeEmit() {
        if (!finished || retained != 0 || emitted || !shouldEmit()) {
            return;
        }
        emitted = true;
        sink.accept(summaryLine());
        for (String line : detailLines()) {
            sink.accept(line);
        }
    }

    private boolean shouldEmit() {
        return snapshot.allMenus() || matchedMenu;
    }

    private String summaryLine() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(fields);
        values.put("total", formatNanos(System.nanoTime() - startedNanos));
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            values.put(entry.getKey(), Long.toString(entry.getValue()));
        }
        for (Map.Entry<String, Long> entry : durations.entrySet()) {
            values.put(entry.getKey(), formatNanos(entry.getValue()));
        }
        return formatLine("summary", values);
    }

    private List<String> detailLines() {
        if (slowDetails.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, SlowDetail> entry : slowDetails.entrySet()) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>(fields);
            values.put("detail", entry.getKey());
            values.put("duration", formatNanos(entry.getValue().nanos()));
            values.put("message", entry.getValue().message());
            lines.add(formatLine("detail", values));
        }
        return List.copyOf(lines);
    }

    private String formatLine(String prefix, Map<String, String> values) {
        StringBuilder builder = new StringBuilder(prefix);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            builder.append(' ')
                    .append(entry.getKey())
                    .append('=')
                    .append(quoted(entry.getValue()));
        }
        return builder.toString();
    }

    private static String quoted(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return '"' + escaped + '"';
    }

    private static String formatNanos(long nanos) {
        return String.format(Locale.ROOT, "%.3fms", nanos / 1_000_000.0d);
    }

    private record SlowDetail(long nanos, String message) {
    }
}
