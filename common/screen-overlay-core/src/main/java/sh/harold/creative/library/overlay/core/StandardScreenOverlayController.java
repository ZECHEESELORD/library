package sh.harold.creative.library.overlay.core;

import net.kyori.adventure.key.Key;
import sh.harold.creative.library.overlay.OverlayConflictPolicy;
import sh.harold.creative.library.overlay.ScreenOverlay;
import sh.harold.creative.library.overlay.ScreenOverlayHandle;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class StandardScreenOverlayController implements AutoCloseable {
    private static final long NANOS_PER_TICK = 50_000_000L;

    private final Map<Key, ActiveOverlay> activeByKey = new LinkedHashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    public ScreenOverlayHandle show(ScreenOverlayRequest request) {
        ensureOpen();
        ScreenOverlayRequest value = Objects.requireNonNull(request, "request");
        purgeExpired();

        Key key = value.key();
        if (value.overlay().conflictPolicy() == OverlayConflictPolicy.REPLACE_ALL) {
            activeByKey.clear();
        } else {
            activeByKey.remove(key);
        }

        ActiveOverlay overlay = new ActiveOverlay(key, value.overlay(), nextGeneration++, currentTick);
        activeByKey.put(key, overlay);
        return new Handle(key, overlay.generation());
    }

    public void advance() {
        ensureOpen();
        currentTick = Math.addExact(currentTick, 1L);
        purgeExpired();
    }

    public ScreenOverlayComposite composite() {
        ensureOpen();
        purgeExpired();
        if (activeByKey.isEmpty()) {
            return ScreenOverlayComposite.hidden();
        }

        double outAlpha = 0.0;
        double outRed = 0.0;
        double outGreen = 0.0;
        double outBlue = 0.0;

        for (ActiveOverlay active : activeByKey.values()) {
            double srcAlpha = active.opacityAt(currentTick);
            if (srcAlpha <= 0.0) {
                continue;
            }

            int rgb = active.overlay().color().value();
            double srcRed = ((rgb >> 16) & 0xFF) / 255.0;
            double srcGreen = ((rgb >> 8) & 0xFF) / 255.0;
            double srcBlue = (rgb & 0xFF) / 255.0;

            outRed = (srcRed * srcAlpha) + (outRed * (1.0 - srcAlpha));
            outGreen = (srcGreen * srcAlpha) + (outGreen * (1.0 - srcAlpha));
            outBlue = (srcBlue * srcAlpha) + (outBlue * (1.0 - srcAlpha));
            outAlpha = srcAlpha + (outAlpha * (1.0 - srcAlpha));
        }

        if (outAlpha <= 0.0) {
            return ScreenOverlayComposite.hidden();
        }

        int rgb = ((clampToByte(outRed / outAlpha) & 0xFF) << 16)
                | ((clampToByte(outGreen / outAlpha) & 0xFF) << 8)
                | (clampToByte(outBlue / outAlpha) & 0xFF);
        return new ScreenOverlayComposite(true, rgb, (float) outAlpha);
    }

    public boolean hasActiveOverlays() {
        if (closed) {
            return false;
        }
        purgeExpired();
        return !activeByKey.isEmpty();
    }

    public void clear(Key key) {
        ensureOpen();
        activeByKey.remove(Objects.requireNonNull(key, "key"));
    }

    public void clearAll() {
        ensureOpen();
        activeByKey.clear();
    }

    @Override
    public void close() {
        closed = true;
        activeByKey.clear();
    }

    long currentTick() {
        return currentTick;
    }

    private boolean active(Key key, long generation) {
        if (closed) {
            return false;
        }
        purgeExpired();
        ActiveOverlay active = activeByKey.get(key);
        return active != null && active.generation() == generation;
    }

    private void clear(Key key, long generation) {
        if (closed) {
            return;
        }
        purgeExpired();
        ActiveOverlay active = activeByKey.get(key);
        if (active != null && active.generation() == generation) {
            activeByKey.remove(key);
        }
    }

    private void purgeExpired() {
        if (closed) {
            return;
        }
        Iterator<ActiveOverlay> iterator = activeByKey.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().expired(currentTick)) {
                iterator.remove();
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Screen overlay controller is closed");
        }
    }

    private static int clampToByte(double normalized) {
        return (int) Math.max(0, Math.min(255, Math.round(normalized * 255.0)));
    }

    private static long toTicks(Duration duration) {
        if (duration.isZero()) {
            return 0L;
        }
        try {
            return Math.max(1L, Math.ceilDiv(duration.toNanos(), NANOS_PER_TICK));
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private record ActiveOverlay(
            Key key,
            ScreenOverlay overlay,
            long generation,
            long startTick,
            long fadeInTicks,
            long holdTicks,
            long fadeOutTicks,
            long totalTicks
    ) {
        private ActiveOverlay(Key key, ScreenOverlay overlay, long generation, long startTick) {
            this(
                    key,
                    overlay,
                    generation,
                    startTick,
                    toTicks(overlay.fadeIn()),
                    toTicks(overlay.hold()),
                    toTicks(overlay.fadeOut()),
                    Math.addExact(Math.addExact(toTicks(overlay.fadeIn()), toTicks(overlay.hold())), toTicks(overlay.fadeOut()))
            );
        }

        boolean expired(long tick) {
            if (totalTicks == 0L) {
                return true;
            }
            return Math.max(0L, tick - startTick) >= totalTicks;
        }

        double opacityAt(long tick) {
            if (overlay.opacity() == 0.0f || totalTicks == 0L) {
                return 0.0;
            }
            long age = Math.max(0L, tick - startTick);
            if (age >= totalTicks) {
                return 0.0;
            }
            if (fadeInTicks > 0L && age < fadeInTicks) {
                return overlay.opacity() * ((double) age / (double) fadeInTicks);
            }
            if (age < fadeInTicks + holdTicks) {
                return overlay.opacity();
            }
            if (fadeOutTicks > 0L) {
                long fadeAge = age - fadeInTicks - holdTicks;
                double multiplier = 1.0 - ((double) fadeAge / (double) fadeOutTicks);
                return overlay.opacity() * Math.max(0.0, multiplier);
            }
            return 0.0;
        }
    }

    private final class Handle implements ScreenOverlayHandle {
        private final Key key;
        private final long generation;

        private Handle(Key key, long generation) {
            this.key = key;
            this.generation = generation;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public boolean active() {
            return StandardScreenOverlayController.this.active(key, generation);
        }

        @Override
        public void close() {
            StandardScreenOverlayController.this.clear(key, generation);
        }
    }
}
