package sh.harold.library.menu.fabric;

import net.minecraft.world.item.ItemStack;
import sh.harold.library.menu.MenuCustodyDecision;
import sh.harold.library.menu.MenuCustodyDestination;
import sh.harold.library.menu.MenuCustodyFailure;
import sh.harold.library.menu.MenuCustodyGesture;
import sh.harold.library.menu.MenuCustodySnapshot;
import sh.harold.library.menu.MenuStack;
import sh.harold.library.menu.MenuViewerSlot;
import sh.harold.library.menu.core.MenuCustodyLedger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns exact native stacks while a reactive menu has custody of them. */
final class FabricMenuCustody {

    private final MenuCustodyLedger<ItemStack> ledger;
    private boolean transitioning;

    FabricMenuCustody(Map<String, Integer> targetSlots) {
        ledger = new MenuCustodyLedger<>(targetSlots);
    }

    MenuCustodyLedger<ItemStack> ledger() {
        return ledger;
    }

    boolean enabled() {
        return !ledger.targetSlots().isEmpty();
    }

    boolean empty() {
        return ledger.empty();
    }

    boolean nativeViewReady(NativeAccess access) {
        Objects.requireNonNull(access, "access");
        for (String key : ledger.targetSlots().keySet()) {
            MenuCustodyLedger.Entry<ItemStack> entry = ledger.target(key).orElse(null);
            if (entry == null) {
                if (!access.targetReady(key)) {
                    return false;
                }
            } else if (!same(access.readTarget(key), entry.nativeItem())) {
                return false;
            }
        }
        ItemStack expectedCursor = ledger.cursor()
                .map(MenuCustodyLedger.Entry::nativeItem)
                .orElse(ItemStack.EMPTY);
        return same(access.readCursor(), expectedCursor);
    }

    boolean transitioning() {
        return transitioning;
    }

    boolean beginTransition() {
        if (transitioning) {
            return false;
        }
        transitioning = true;
        return true;
    }

    void endTransition() {
        transitioning = false;
    }

    Outcome rejectGesture(MenuCustodyGesture gesture, MenuCustodyFailure failure) {
        return reject(Objects.requireNonNull(gesture, "gesture"), Objects.requireNonNull(failure, "failure"));
    }

    Outcome transact(MenuCustodyGesture gesture, MenuCustodyDecision decision, ItemStack observedItem,
                     NativeAccess access) {
        Objects.requireNonNull(gesture, "gesture");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(access, "access");
        if (transitioning) {
            return reject(gesture, MenuCustodyFailure.TRANSITION_IN_PROGRESS);
        }
        if (!(decision instanceof MenuCustodyDecision.Move move)) {
            return reject(gesture, MenuCustodyFailure.POLICY_REJECTED);
        }
        try {
            return switch (gesture) {
                case MenuCustodyGesture.ViewerClick viewer ->
                        executeViewer(viewer, move.destination(), observedItem, access);
                case MenuCustodyGesture.TargetClick target -> executeTarget(target, move.destination(), access);
                case MenuCustodyGesture.TargetDrag drag -> executeDrag(drag, move.destination(), access);
                case MenuCustodyGesture.OutsideClick outside -> executeOutside(outside, move.destination(), access);
                case MenuCustodyGesture.Settle ignored -> reject(gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
            };
        } catch (RuntimeException exception) {
            return reject(gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
        }
    }

    List<Outcome> settle(MenuCustodyGesture.SettleReason reason, NativeAccess access) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(access, "access");
        MenuCustodyGesture gesture = new MenuCustodyGesture.Settle(reason);
        if (ledger.cursor().isPresent()) {
            Outcome cursor = settleCursor(gesture, access);
            if (!cursor.committed()) {
                return List.of(cursor);
            }
        }
        for (String key : ledger.targetSlots().keySet()) {
            if (ledger.target(key).isPresent()) {
                Outcome target = release(
                        gesture, new MenuCustodyLedger.Source.Target(key), access, true);
                if (!target.committed()) {
                    return List.of(target);
                }
            }
        }
        return List.of(commit(ledger.nextOperationId(), gesture));
    }

    private Outcome settleCursor(MenuCustodyGesture gesture, NativeAccess access) {
        MenuCustodyLedger.Entry<ItemStack> entry = ledger.cursor().orElseThrow();
        ItemStack physical = copy(access.readCursor());
        if (same(physical, entry.nativeItem())) {
            return release(gesture, new MenuCustodyLedger.Source.Cursor(), access, true);
        }
        if (!physical.isEmpty()) {
            return reject(gesture, MenuCustodyFailure.STALE_INPUT);
        }
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareTransfer(
                    new MenuCustodyLedger.Source.Cursor(),
                    new MenuCustodyLedger.Destination.Released());
            ledger.commit(transfer);
        } catch (RuntimeException exception) {
            return reject(gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
        }
        return commit(transfer.operationId(), gesture);
    }

    private Outcome executeViewer(MenuCustodyGesture.ViewerClick gesture, MenuCustodyDestination destination,
                                  ItemStack observedItem, NativeAccess access) {
        MenuViewerSlot observation = gesture.slot();
        int slot = observation.slot();
        if (slot >= access.viewerSize()) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }
        ItemStack observed = copy(observedItem);
        if (!same(access.readViewer(slot), observed)) {
            return reject(gesture, MenuCustodyFailure.STALE_INPUT);
        }
        if (!observed.isEmpty()) {
            if (observation.item() == null) {
                return reject(gesture, MenuCustodyFailure.STALE_INPUT);
            }
            return acquire(gesture, observation.item(), observed, destination, access);
        }
        if (observation.item() != null || ledger.cursor().isEmpty()) {
            return reject(gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
        }
        if (!(destination instanceof MenuCustodyDestination.ViewerSlot viewer)
                || !sameObservation(observation, viewer.slot())) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }
        return release(gesture, new MenuCustodyLedger.Source.Cursor(), access, true, slot);
    }

    private Outcome acquire(MenuCustodyGesture gesture, MenuStack presentation, ItemStack observed,
                            MenuCustodyDestination destination, NativeAccess access) {
        MenuCustodyLedger.Destination ledgerDestination;
        if (destination instanceof MenuCustodyDestination.Cursor) {
            if (ledger.cursor().isPresent() || !access.readCursor().isEmpty()) {
                return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            }
            ledgerDestination = new MenuCustodyLedger.Destination.Cursor();
        } else if (destination instanceof MenuCustodyDestination.Target target) {
            if (!ledger.targetSlots().containsKey(target.key())) {
                return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
            }
            if (ledger.target(target.key()).isPresent()) {
                return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            }
            ledgerDestination = new MenuCustodyLedger.Destination.Target(target.key());
        } else {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }

        MenuCustodyGesture.ViewerClick viewer = (MenuCustodyGesture.ViewerClick) gesture;
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareAcquire(observed.copy(), presentation, viewer.slot().slot(), ledgerDestination);
        } catch (IllegalStateException exception) {
            return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
        } catch (IllegalArgumentException exception) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }

        BeforeImages before = new BeforeImages(access);
        Location source = new Location.Viewer(viewer.slot().slot());
        Location target = location(ledgerDestination);
        before.capture(source);
        before.capture(target);
        try {
            if (!same(access.readViewer(viewer.slot().slot()), observed)) {
                return reject(transfer.operationId(), gesture, MenuCustodyFailure.STALE_INPUT);
            }
            if (ledgerDestination instanceof MenuCustodyLedger.Destination.Target targetDestination
                    && !access.targetReady(targetDestination.key())) {
                return reject(
                        transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            }
            before.write(source, ItemStack.EMPTY);
            before.write(target, transfer.entry().nativeItem());
            ledger.commit(transfer);
        } catch (RuntimeException exception) {
            before.rollback();
            return reject(transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
        }
        return commit(transfer.operationId(), gesture);
    }

    private Outcome executeTarget(MenuCustodyGesture.TargetClick gesture, MenuCustodyDestination destination,
                                  NativeAccess access) {
        if (!ledger.targetSlots().containsKey(gesture.targetKey())) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }
        boolean cursorOccupied = ledger.cursor().isPresent();
        boolean targetOccupied = ledger.target(gesture.targetKey()).isPresent();
        if (cursorOccupied && targetOccupied) {
            return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
        }
        if (!cursorOccupied && !targetOccupied) {
            return reject(gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
        }
        if (cursorOccupied) {
            if (!(destination instanceof MenuCustodyDestination.Target target)
                    || !target.key().equals(gesture.targetKey())) {
                return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
            }
            return transfer(gesture, new MenuCustodyLedger.Source.Cursor(),
                    new MenuCustodyLedger.Destination.Target(gesture.targetKey()), access);
        }
        MenuCustodyLedger.Source source = new MenuCustodyLedger.Source.Target(gesture.targetKey());
        if (destination instanceof MenuCustodyDestination.Cursor) {
            if (ledger.cursor().isPresent() || !access.readCursor().isEmpty()) {
                return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
            }
            return transfer(gesture, source, new MenuCustodyLedger.Destination.Cursor(), access);
        }
        if (destination instanceof MenuCustodyDestination.Origin) {
            return releaseOrigin(gesture, source, access);
        }
        return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
    }

    private Outcome executeDrag(MenuCustodyGesture.TargetDrag gesture, MenuCustodyDestination destination,
                                NativeAccess access) {
        if (gesture.targetKeys().size() != 1
                || ledger.cursor().isEmpty() || !(destination instanceof MenuCustodyDestination.Target target)
                || !gesture.targetKeys().contains(target.key())) {
            return reject(gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
        }
        if (!ledger.targetSlots().containsKey(target.key())) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }
        if (ledger.target(target.key()).isPresent()) {
            return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
        }
        return transfer(gesture, new MenuCustodyLedger.Source.Cursor(),
                new MenuCustodyLedger.Destination.Target(target.key()), access);
    }

    private Outcome executeOutside(MenuCustodyGesture.OutsideClick gesture, MenuCustodyDestination destination,
                                   NativeAccess access) {
        if (ledger.cursor().isEmpty()) {
            return reject(gesture, MenuCustodyFailure.UNSUPPORTED_GESTURE);
        }
        if (!(destination instanceof MenuCustodyDestination.Origin)) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }
        return releaseOrigin(gesture, new MenuCustodyLedger.Source.Cursor(), access);
    }

    private Outcome transfer(MenuCustodyGesture gesture, MenuCustodyLedger.Source source,
                             MenuCustodyLedger.Destination destination, NativeAccess access) {
        MenuCustodyLedger.Entry<ItemStack> entry = entry(source);
        if (entry == null) {
            return reject(gesture, MenuCustodyFailure.STALE_INPUT);
        }
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareTransfer(source, destination);
        } catch (IllegalStateException exception) {
            return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
        } catch (IllegalArgumentException exception) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }

        BeforeImages before = new BeforeImages(access);
        Location sourceLocation = location(source);
        Location destinationLocation = location(destination);
        before.capture(sourceLocation);
        before.capture(destinationLocation);
        try {
            if (!same(before.read(sourceLocation), entry.nativeItem())) {
                return reject(transfer.operationId(), gesture, MenuCustodyFailure.STALE_INPUT);
            }
            if (destination instanceof MenuCustodyLedger.Destination.Target targetDestination
                    && !access.targetReady(targetDestination.key())) {
                return reject(
                        transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
            }
            before.write(sourceLocation, ItemStack.EMPTY);
            before.write(destinationLocation, entry.nativeItem());
            ledger.commit(transfer);
        } catch (RuntimeException exception) {
            before.rollback();
            return reject(transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
        }
        return commit(transfer.operationId(), gesture);
    }

    private Outcome release(MenuCustodyGesture gesture, MenuCustodyLedger.Source source, NativeAccess access,
                            boolean verifySource) {
        MenuCustodyLedger.Entry<ItemStack> entry = entry(source);
        if (entry == null) {
            return reject(gesture, MenuCustodyFailure.STALE_INPUT);
        }
        return release(gesture, source, access, verifySource, releaseSlot(access, entry.originalViewerSlot()));
    }

    private Outcome releaseOrigin(MenuCustodyGesture gesture, MenuCustodyLedger.Source source, NativeAccess access) {
        MenuCustodyLedger.Entry<ItemStack> entry = entry(source);
        if (entry == null) {
            return reject(gesture, MenuCustodyFailure.STALE_INPUT);
        }
        int origin = entry.originalViewerSlot();
        if (origin >= access.viewerSize()) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }
        if (!access.readViewer(origin).isEmpty()) {
            return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
        }
        return release(gesture, source, access, true, origin);
    }

    private Outcome release(MenuCustodyGesture gesture, MenuCustodyLedger.Source source, NativeAccess access,
                            boolean verifySource, int destinationSlot) {
        MenuCustodyLedger.Entry<ItemStack> entry = entry(source);
        if (entry == null) {
            return reject(gesture, MenuCustodyFailure.STALE_INPUT);
        }
        if (destinationSlot >= 0 && !access.readViewer(destinationSlot).isEmpty()) {
            return reject(gesture, MenuCustodyFailure.OCCUPIED_DESTINATION);
        }
        MenuCustodyLedger.PreparedTransfer<ItemStack> transfer;
        try {
            transfer = ledger.prepareTransfer(source, new MenuCustodyLedger.Destination.Released());
        } catch (RuntimeException exception) {
            return reject(gesture, MenuCustodyFailure.INVALID_DESTINATION);
        }

        BeforeImages before = new BeforeImages(access);
        Location sourceLocation = location(source);
        before.capture(sourceLocation);
        if (destinationSlot >= 0) {
            before.capture(new Location.Viewer(destinationSlot));
        }
        try {
            if (verifySource && !same(before.read(sourceLocation), entry.nativeItem())) {
                return reject(transfer.operationId(), gesture, MenuCustodyFailure.STALE_INPUT);
            }
            before.write(sourceLocation, ItemStack.EMPTY);
            if (destinationSlot >= 0) {
                before.write(new Location.Viewer(destinationSlot), entry.nativeItem());
                ledger.commit(transfer);
            } else {
                ledger.commit(transfer);
                try {
                    if (!access.drop(entry.nativeItem().copy())) {
                        throw new IllegalStateException("Could not release custody item");
                    }
                } catch (RuntimeException exception) {
                    return reject(transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
                }
            }
        } catch (RuntimeException exception) {
            before.rollback();
            return reject(transfer.operationId(), gesture, MenuCustodyFailure.NATIVE_MUTATION_FAILED);
        }
        return commit(transfer.operationId(), gesture);
    }

    private MenuCustodyLedger.Entry<ItemStack> entry(MenuCustodyLedger.Source source) {
        return switch (source) {
            case MenuCustodyLedger.Source.Cursor ignored -> ledger.cursor().orElse(null);
            case MenuCustodyLedger.Source.Target target -> ledger.target(target.key()).orElse(null);
        };
    }

    private Outcome commit(long operationId, MenuCustodyGesture gesture) {
        return new Outcome(operationId, gesture, null, ledger.snapshot());
    }

    private Outcome reject(MenuCustodyGesture gesture, MenuCustodyFailure failure) {
        return reject(ledger.nextOperationId(), gesture, failure);
    }

    private Outcome reject(long operationId, MenuCustodyGesture gesture, MenuCustodyFailure failure) {
        return new Outcome(operationId, gesture, Objects.requireNonNull(failure, "failure"), ledger.snapshot());
    }

    private static int releaseSlot(NativeAccess access, int preferred) {
        if (preferred >= 0 && preferred < access.viewerSize() && access.readViewer(preferred).isEmpty()) {
            return preferred;
        }
        for (int slot = 0; slot < access.viewerSize(); slot++) {
            if (access.readViewer(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean sameObservation(MenuViewerSlot left, MenuViewerSlot right) {
        return left.observationId() == right.observationId()
                && left.slot() == right.slot()
                && Objects.equals(left.item(), right.item());
    }

    private static boolean same(ItemStack left, ItemStack right) {
        return ItemStack.matches(copy(left), copy(right));
    }

    private static ItemStack copy(ItemStack item) {
        return item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
    }

    private static Location location(MenuCustodyLedger.Source source) {
        return switch (source) {
            case MenuCustodyLedger.Source.Cursor ignored -> new Location.Cursor();
            case MenuCustodyLedger.Source.Target target -> new Location.Target(target.key());
        };
    }

    private static Location location(MenuCustodyLedger.Destination destination) {
        return switch (destination) {
            case MenuCustodyLedger.Destination.Cursor ignored -> new Location.Cursor();
            case MenuCustodyLedger.Destination.Target target -> new Location.Target(target.key());
            case MenuCustodyLedger.Destination.Released ignored ->
                    throw new IllegalArgumentException("Released custody has no inventory location");
        };
    }

    record Outcome(long operationId, MenuCustodyGesture gesture, MenuCustodyFailure failure,
                   MenuCustodySnapshot snapshot) {

        Outcome {
            if (operationId <= 0L) {
                throw new IllegalArgumentException("operationId must be greater than zero");
            }
            Objects.requireNonNull(gesture, "gesture");
            Objects.requireNonNull(snapshot, "snapshot");
        }

        boolean committed() {
            return failure == null;
        }
    }

    interface NativeAccess {

        int viewerSize();

        ItemStack readViewer(int slot);

        void writeViewer(int slot, ItemStack item);

        ItemStack readCursor();

        void writeCursor(ItemStack item);

        ItemStack readTarget(String key);

        boolean targetReady(String key);

        void writeTarget(String key, ItemStack item);

        boolean drop(ItemStack item);
    }

    private sealed interface Location permits Location.Cursor, Location.Target, Location.Viewer {

        record Cursor() implements Location {
        }

        record Target(String key) implements Location {
        }

        record Viewer(int slot) implements Location {
        }
    }

    private static final class BeforeImages {

        private final NativeAccess access;
        private final Map<Location, ItemStack> images = new LinkedHashMap<>();

        private BeforeImages(NativeAccess access) {
            this.access = access;
        }

        private void capture(Location location) {
            images.computeIfAbsent(location, ignored -> copy(read(location)));
        }

        private ItemStack read(Location location) {
            return switch (location) {
                case Location.Cursor ignored -> access.readCursor();
                case Location.Target target -> access.readTarget(target.key());
                case Location.Viewer viewer -> access.readViewer(viewer.slot());
            };
        }

        private void write(Location location, ItemStack item) {
            ItemStack exact = copy(item);
            switch (location) {
                case Location.Cursor ignored -> access.writeCursor(exact);
                case Location.Target target -> access.writeTarget(target.key(), exact);
                case Location.Viewer viewer -> access.writeViewer(viewer.slot(), exact);
            }
        }

        private void rollback() {
            Object[] locations = images.keySet().toArray();
            for (int index = locations.length - 1; index >= 0; index--) {
                Location location = (Location) locations[index];
                try {
                    write(location, images.get(location));
                } catch (RuntimeException ignored) {
                    // An uncleared destination makes restoring the source unsafe: stop rather than duplicate.
                    return;
                }
            }
        }
    }
}
