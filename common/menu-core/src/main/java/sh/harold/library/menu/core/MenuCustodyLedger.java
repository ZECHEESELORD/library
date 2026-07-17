package sh.harold.library.menu.core;

import sh.harold.library.menu.MenuCustodyItem;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuStack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MenuCustodyLedger<N> {

    private final Map<String, Integer> targetSlots;
    private final Map<Integer, String> targetsBySlot;
    private final Map<String, Entry<N>> targets = new LinkedHashMap<>();
    private long revision;
    private long nextAssetId;
    private long nextOperationId;
    private Entry<N> cursor;

    public MenuCustodyLedger(Map<String, Integer> targetSlots) {
        Objects.requireNonNull(targetSlots, "targetSlots");
        Map<String, Integer> validated = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> target : targetSlots.entrySet()) {
            String key = Objects.requireNonNull(target.getKey(), "target key");
            Integer slot = Objects.requireNonNull(target.getValue(), "target slot");
            if (key.isBlank()) {
                throw new IllegalArgumentException("target key cannot be blank");
            }
            if (slot < 0 || slot > 53) {
                throw new IllegalArgumentException("target slot must be between 0 and 53");
            }
            if (validated.containsValue(slot)) {
                throw new IllegalArgumentException("target slots must be unique");
            }
            validated.put(key, slot);
        }
        this.targetSlots = Collections.unmodifiableMap(new LinkedHashMap<>(validated));
        Map<Integer, String> inverse = new LinkedHashMap<>();
        validated.forEach((key, slot) -> inverse.put(slot, key));
        this.targetsBySlot = Collections.unmodifiableMap(inverse);
    }

    public Map<String, Integer> targetSlots() {
        return targetSlots;
    }

    public Optional<String> targetAt(int slot) {
        return Optional.ofNullable(targetsBySlot.get(slot));
    }

    public Optional<Entry<N>> cursor() {
        return Optional.ofNullable(cursor);
    }

    public Optional<Entry<N>> target(String key) {
        return Optional.ofNullable(targets.get(key));
    }

    public boolean empty() {
        return cursor == null && targets.isEmpty();
    }

    public long nextOperationId() {
        return ++nextOperationId;
    }

    public PreparedTransfer<N> prepareAcquire(N nativeItem, MenuStack presentation, int originalViewerSlot,
                                               Destination destination) {
        if (originalViewerSlot < 0) {
            throw new IllegalArgumentException("originalViewerSlot cannot be negative");
        }
        validateDestination(destination);
        Entry<N> entry = new Entry<>(++nextAssetId, originalViewerSlot,
                Objects.requireNonNull(nativeItem, "nativeItem"),
                Objects.requireNonNull(presentation, "presentation"));
        return new LedgerPreparedTransfer<>(this, nextOperationId(), revision, null, destination, entry);
    }

    public PreparedTransfer<N> prepareTransfer(Source source, Destination destination) {
        Entry<N> entry = requireSource(source);
        validateDestination(destination);
        return new LedgerPreparedTransfer<>(this, nextOperationId(), revision, source, destination, entry);
    }

    public void commit(PreparedTransfer<N> transfer) {
        Objects.requireNonNull(transfer, "transfer");
        if (!(transfer instanceof LedgerPreparedTransfer<?> prepared) || prepared.owner() != this) {
            throw new IllegalArgumentException("Custody transfer belongs to another ledger");
        }
        if (transfer.expectedRevision() != revision) {
            throw new IllegalStateException("Custody transfer is stale");
        }
        if (transfer.source() != null && requireSource(transfer.source()) != transfer.entry()) {
            throw new IllegalStateException("Custody source changed before commit");
        }
        if (transfer.source() != null) {
            remove(transfer.source());
        }
        place(transfer.destination(), transfer.entry());
        revision++;
    }

    public MenuCustodySnapshot snapshot() {
        Map<String, MenuCustodyItem> snapshotTargets = new LinkedHashMap<>();
        targets.forEach((key, entry) -> snapshotTargets.put(key, entry.publicItem()));
        return new MenuCustodySnapshot(snapshotTargets,
                cursor == null ? Optional.empty() : Optional.of(cursor.publicItem()));
    }

    private Entry<N> requireSource(Source source) {
        Objects.requireNonNull(source, "source");
        Entry<N> entry = switch (source) {
            case Source.Cursor ignored -> cursor;
            case Source.Target target -> targets.get(target.key());
        };
        if (entry == null) {
            throw new IllegalStateException("Custody source is empty");
        }
        return entry;
    }

    private void validateDestination(Destination destination) {
        Objects.requireNonNull(destination, "destination");
        switch (destination) {
            case Destination.Cursor ignored -> {
                if (cursor != null) {
                    throw new IllegalStateException("Custody cursor is occupied");
                }
            }
            case Destination.Target target -> {
                if (!targetSlots.containsKey(target.key())) {
                    throw new IllegalArgumentException("Unknown custody target: " + target.key());
                }
                if (targets.containsKey(target.key())) {
                    throw new IllegalStateException("Custody target is occupied");
                }
            }
            case Destination.Released ignored -> {
            }
        }
    }

    private void remove(Source source) {
        switch (source) {
            case Source.Cursor ignored -> cursor = null;
            case Source.Target target -> targets.remove(target.key());
        }
    }

    private void place(Destination destination, Entry<N> entry) {
        switch (destination) {
            case Destination.Cursor ignored -> cursor = entry;
            case Destination.Target target -> targets.put(target.key(), entry);
            case Destination.Released ignored -> {
            }
        }
    }

    public record Entry<N>(long assetId, int originalViewerSlot, N nativeItem, MenuStack presentation) {

        MenuCustodyItem publicItem() {
            return new MenuCustodyItem(presentation, originalViewerSlot);
        }
    }

    public sealed interface PreparedTransfer<N> permits LedgerPreparedTransfer {

        long operationId();

        long expectedRevision();

        Source source();

        Destination destination();

        Entry<N> entry();
    }

    private record LedgerPreparedTransfer<N>(
            MenuCustodyLedger<N> owner,
            long operationId,
            long expectedRevision,
            Source source,
            Destination destination,
            Entry<N> entry
    ) implements PreparedTransfer<N> {
    }

    public sealed interface Source permits Source.Cursor, Source.Target {

        record Cursor() implements Source {
        }

        record Target(String key) implements Source {
            public Target {
                Objects.requireNonNull(key, "key");
            }
        }
    }

    public sealed interface Destination permits Destination.Cursor, Destination.Released, Destination.Target {

        record Cursor() implements Destination {
        }

        record Target(String key) implements Destination {
            public Target {
                Objects.requireNonNull(key, "key");
            }
        }

        record Released() implements Destination {
        }
    }
}
