package sh.harold.creative.library.cooldown.core;

import sh.harold.creative.library.cooldown.CooldownAcquisition;
import sh.harold.creative.library.cooldown.CooldownKey;
import sh.harold.creative.library.cooldown.CooldownPolicy;
import sh.harold.creative.library.cooldown.CooldownRegistry;
import sh.harold.creative.library.cooldown.CooldownSpec;
import sh.harold.creative.library.cooldown.CooldownTicket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory cooldown registry backed by concurrent maps and a background reaper.
 */
public final class InMemoryCooldownRegistry implements CooldownRegistry {

    private static final Logger LOGGER = Logger.getLogger(InMemoryCooldownRegistry.class.getName());
    private static final Duration REAPER_IDLE_POLL = Duration.ofMillis(5);

    private final Clock clock;
    private final ConcurrentMap<CooldownKey, Entry> store = new ConcurrentHashMap<>();
    private final DelayQueue<Expiry> expiries = new DelayQueue<>();
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread reaperThread;

    public InMemoryCooldownRegistry() {
        this(Clock.systemUTC());
    }

    public InMemoryCooldownRegistry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reaperThread = createReaperThread();
    }

    @Override
    public CooldownAcquisition acquire(CooldownKey key, CooldownSpec spec) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(spec, "spec");

        Instant now = clock.instant();
        CooldownAcquisition[] acquisition = new CooldownAcquisition[1];
        store.compute(key, (ignored, existing) -> acquireEntry(key, spec, now, existing, acquisition));
        return Objects.requireNonNull(acquisition[0], "acquisition");
    }

    @Override
    public Optional<Duration> remaining(CooldownKey key) {
        Objects.requireNonNull(key, "key");

        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        if (entry.isExpired(now)) {
            store.remove(key, entry);
            return Optional.empty();
        }

        return Optional.of(Duration.between(now, entry.expiresAt()));
    }

    @Override
    public void clear(CooldownKey key) {
        Objects.requireNonNull(key, "key");
        store.remove(key);
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            reaperThread.interrupt();
            try {
                reaperThread.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        store.clear();
        expiries.clear();
    }

    int drainExpired(int maxBatch) {
        if (maxBatch < 0) {
            throw new IllegalArgumentException("maxBatch must be >= 0");
        }
        if (maxBatch == 0) {
            return 0;
        }

        int removed = 0;
        Instant now = clock.instant();
        Iterator<Map.Entry<CooldownKey, Entry>> iterator = store.entrySet().iterator();
        while (iterator.hasNext() && removed < maxBatch) {
            Map.Entry<CooldownKey, Entry> entry = iterator.next();
            if (entry.getValue().isExpired(now) && store.remove(entry.getKey(), entry.getValue())) {
                removed++;
            }
        }
        return removed;
    }

    int trackedCount() {
        return store.size();
    }

    private Thread createReaperThread() {
        Runnable task = this::runReaperLoop;
        try {
            return Thread.ofVirtual()
                    .name("cooldown-reaper-" + Integer.toHexString(System.identityHashCode(this)))
                    .start(task);
        } catch (UnsupportedOperationException ignored) {
            return Thread.ofPlatform()
                    .daemon(true)
                    .name("cooldown-reaper-" + Integer.toHexString(System.identityHashCode(this)))
                    .start(task);
        }
    }

    private void runReaperLoop() {
        while (running.get()) {
            try {
                Expiry expiry = expiries.take();
                processExpiry(expiry);
            } catch (InterruptedException interrupted) {
                if (!running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                LockSupport.parkNanos(REAPER_IDLE_POLL.toNanos());
            } catch (Throwable throwable) {
                LOGGER.log(Level.WARNING, "Cooldown reaper failure", throwable);
            }
        }
    }

    private void processExpiry(Expiry expiry) {
        store.computeIfPresent(expiry.key(), (key, entry) -> {
            if (entry.generation() != expiry.generation()) {
                return entry;
            }
            return entry.isExpired(clock.instant()) ? null : entry;
        });
    }

    private Entry acquireEntry(CooldownKey key,
                               CooldownSpec spec,
                               Instant now,
                               Entry existing,
                               CooldownAcquisition[] acquisition) {
        if (existing == null || existing.isExpired(now)) {
            Entry created = createEntry(key, now, spec.window());
            acquisition[0] = new CooldownAcquisition.Accepted(new CooldownTicket(key, created.expiresAt()));
            return created;
        }

        if (spec.policy() == CooldownPolicy.EXTEND_ON_ACQUIRE) {
            Entry created = createEntry(key, now, spec.window());
            acquisition[0] = new CooldownAcquisition.Accepted(new CooldownTicket(key, created.expiresAt()));
            return created;
        }

        acquisition[0] = new CooldownAcquisition.Rejected(Duration.between(now, existing.expiresAt()));
        return existing;
    }

    private Entry createEntry(CooldownKey key, Instant now, Duration window) {
        Instant expiresAt = now.plus(window);
        long currentGeneration = generation.incrementAndGet();
        expiries.offer(new Expiry(key, currentGeneration, computeDeadlineNanos(window)));
        return new Entry(expiresAt, currentGeneration);
    }

    private long computeDeadlineNanos(Duration window) {
        long nanos;
        try {
            nanos = window.toNanos();
        } catch (ArithmeticException overflow) {
            nanos = Long.MAX_VALUE;
        }
        if (nanos <= 0L) {
            nanos = 1L;
        }

        long deadline = System.nanoTime() + nanos;
        if (deadline < 0L) {
            return Long.MAX_VALUE;
        }
        return deadline;
    }

    private record Entry(Instant expiresAt, long generation) {
        boolean isExpired(Instant now) {
            return !expiresAt.isAfter(now);
        }
    }

    private record Expiry(CooldownKey key, long generation, long deadlineNanos) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            long delay = deadlineNanos - System.nanoTime();
            return unit.convert(delay, TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) {
                return 0;
            }
            Expiry that = (Expiry) other;
            return Long.compare(deadlineNanos, that.deadlineNanos);
        }
    }
}
