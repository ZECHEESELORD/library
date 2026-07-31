package sh.harold.library.npc.behavior.core;

import net.kyori.adventure.text.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Deterministic, unbounded, actor-tick speech FIFO. This class owns timing but
 * no platform entities.
 */
public final class NpcSpeechQueue {

    private final Deque<Entry> pending = new ArrayDeque<>();
    private final Consumer<NpcBubbleFrame> show;
    private final Consumer<NpcBubbleFrame> update;
    private final java.util.function.LongConsumer clear;
    private final Set<UUID> excludedViewers = new LinkedHashSet<>();
    private Entry active;
    private NpcBubbleFrame visibleFrame;
    private Entry urgentBehindBarrier;
    private Phase phase = Phase.IDLE;
    private long deadline;
    private long nextId;
    private int barrierDepth;

    public NpcSpeechQueue(Consumer<NpcBubbleFrame> show, java.util.function.LongConsumer clear) {
        this(show, show, clear);
    }

    public NpcSpeechQueue(
            Consumer<NpcBubbleFrame> show,
            Consumer<NpcBubbleFrame> update,
            java.util.function.LongConsumer clear
    ) {
        this.show = Objects.requireNonNull(show, "show");
        this.update = Objects.requireNonNull(update, "update");
        this.clear = Objects.requireNonNull(clear, "clear");
    }

    public synchronized Ticket append(Component text, NpcBubbleFrame.Kind kind) {
        Entry entry = new Entry(nextId++, text, kind);
        pending.addLast(entry);
        return entry.ticket;
    }

    /**
     * Clears disposable worldbuilding and installs the newest immediate line.
     * An interruption barrier delays its display without recursively growing a
     * second queue.
     */
    public synchronized Ticket now(Component text, NpcBubbleFrame.Kind kind, long tick) {
        Entry entry = new Entry(nextId++, text, kind);
        cancelPending();
        if (barrierDepth > 0) {
            if (urgentBehindBarrier != null) {
                urgentBehindBarrier.ticket.complete(false);
            }
            urgentBehindBarrier = entry;
            return entry.ticket;
        }
        stopActive(false);
        start(entry, tick);
        return entry.ticket;
    }

    /** Starts a coordinator-owned interruption line while the public barrier is held. */
    public synchronized Ticket nowInsideBarrier(Component text, NpcBubbleFrame.Kind kind, long tick) {
        Entry entry = new Entry(nextId++, text, kind);
        stopActive(false);
        start(entry, tick);
        return entry.ticket;
    }

    public synchronized void tick(long tick) {
        if (active == null) {
            startNextIfAllowed(tick);
            return;
        }
        if (tick < deadline) {
            return;
        }
        if (phase == Phase.VISIBLE) {
            clear.accept(active.id);
            visibleFrame = null;
            phase = Phase.BREATH;
            deadline = tick + NpcSpeechText.BREATH_TICKS;
            return;
        }
        active.ticket.complete(true);
        active = null;
        phase = Phase.IDLE;
        startNextIfAllowed(tick);
    }

    public synchronized Barrier beginInterruptionBarrier() {
        barrierDepth++;
        return new Barrier(this);
    }

    public synchronized void clear(long tick) {
        cancelPending();
        if (urgentBehindBarrier != null) {
            urgentBehindBarrier.ticket.complete(false);
            urgentBehindBarrier = null;
        }
        stopActive(false);
        deadline = tick;
    }

    public synchronized void suppressViewer(UUID viewerId) {
        if (excludedViewers.add(Objects.requireNonNull(viewerId, "viewerId"))) {
            refreshVisibleFrame();
        }
    }

    public synchronized void releaseViewer(UUID viewerId) {
        if (excludedViewers.remove(Objects.requireNonNull(viewerId, "viewerId"))) {
            refreshVisibleFrame();
        }
    }

    public synchronized void clearViewerSuppressions() {
        if (!excludedViewers.isEmpty()) {
            excludedViewers.clear();
            refreshVisibleFrame();
        }
    }

    public synchronized void cancel(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        Entry queued = pending.stream().filter(entry -> entry.ticket == ticket).findFirst().orElse(null);
        if (queued != null) {
            pending.remove(queued);
            queued.ticket.complete(false);
            return;
        }
        if (urgentBehindBarrier != null && urgentBehindBarrier.ticket == ticket) {
            urgentBehindBarrier.ticket.complete(false);
            urgentBehindBarrier = null;
            return;
        }
        if (active != null && active.ticket == ticket) {
            stopActive(false);
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                phase,
                phase == Phase.VISIBLE
                        ? Optional.ofNullable(active).map(entry -> entry.text)
                        : Optional.empty(),
                pending.stream().map(entry -> entry.text).toList(),
                Optional.ofNullable(urgentBehindBarrier).map(entry -> entry.text),
                deadline,
                barrierDepth
        );
    }

    private void closeBarrier() {
        synchronized (this) {
            if (barrierDepth == 0) {
                return;
            }
            barrierDepth--;
        }
    }

    private void startNextIfAllowed(long tick) {
        if (barrierDepth > 0 || active != null) {
            return;
        }
        Entry next;
        if (urgentBehindBarrier != null) {
            next = urgentBehindBarrier;
            urgentBehindBarrier = null;
        } else {
            next = pending.pollFirst();
        }
        if (next != null) {
            start(next, tick);
        }
    }

    private void start(Entry entry, long tick) {
        active = entry;
        phase = Phase.VISIBLE;
        Component wrapped = NpcSpeechText.wrap(entry.text);
        deadline = tick + NpcSpeechText.holdTicks(entry.text);
        visibleFrame = new NpcBubbleFrame(entry.id, wrapped, deadline, entry.kind, excludedViewers);
        show.accept(visibleFrame);
    }

    private void refreshVisibleFrame() {
        if (visibleFrame == null || phase != Phase.VISIBLE) {
            return;
        }
        visibleFrame = new NpcBubbleFrame(
                visibleFrame.id(),
                visibleFrame.text(),
                visibleFrame.expiresAtTick(),
                visibleFrame.kind(),
                excludedViewers
        );
        update.accept(visibleFrame);
    }

    private void cancelPending() {
        List<Entry> canceled = new ArrayList<>(pending);
        pending.clear();
        canceled.forEach(entry -> entry.ticket.complete(false));
    }

    private void stopActive(boolean completed) {
        if (active == null) {
            return;
        }
        if (phase == Phase.VISIBLE) {
            clear.accept(active.id);
        }
        visibleFrame = null;
        active.ticket.complete(completed);
        active = null;
        phase = Phase.IDLE;
    }

    public enum Phase {
        IDLE,
        VISIBLE,
        BREATH
    }

    public record Snapshot(
            Phase phase,
            Optional<Component> visibleText,
            List<Component> pending,
            Optional<Component> urgentBehindBarrier,
            long deadline,
            int barrierDepth
    ) {
        public Snapshot {
            phase = Objects.requireNonNull(phase, "phase");
            visibleText = Objects.requireNonNull(visibleText, "visibleText");
            pending = List.copyOf(Objects.requireNonNull(pending, "pending"));
            urgentBehindBarrier = Objects.requireNonNull(urgentBehindBarrier, "urgentBehindBarrier");
        }
    }

    public static final class Ticket {
        private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

        private Ticket() {
        }

        public CompletionStage<Boolean> completion() {
            return completion;
        }

        public boolean done() {
            return completion.isDone();
        }

        private void complete(boolean naturally) {
            completion.complete(naturally);
        }
    }

    public static final class Barrier implements AutoCloseable {
        private NpcSpeechQueue owner;

        private Barrier(NpcSpeechQueue owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            NpcSpeechQueue current = owner;
            if (current != null) {
                owner = null;
                current.closeBarrier();
            }
        }
    }

    private static final class Entry {
        private final long id;
        private final Component text;
        private final NpcBubbleFrame.Kind kind;
        private final Ticket ticket = new Ticket();

        private Entry(long id, Component text, NpcBubbleFrame.Kind kind) {
            this.id = id;
            this.text = Objects.requireNonNull(text, "text");
            this.kind = Objects.requireNonNull(kind, "kind");
        }
    }
}
